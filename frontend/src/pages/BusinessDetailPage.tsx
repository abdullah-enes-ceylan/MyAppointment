import { useState, useEffect, useMemo } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  ArrowLeft,
  BadgeCheck,
  Check,
  CheckCircle2,
  Clock,
  Heart,
  MapPin,
  Phone,
  Star,
} from "lucide-react";
import axios from "axios";
import api from "../api/axios";
import { getErrorMessage } from "../api/errors";
import Toast from "../components/Toast";
import LocationPicker from "../components/LocationPicker";
import StarRating from "../components/StarRating";
import { getCategory, getCategoryLabel, getGenderLabel } from "../components/CategoryIcons";
import { useAuth } from "../context/AuthContext";
import { resolvePhotoUrl } from "../utils/photo";
import type { AppointmentRequest, BusinessDetailResponse, ReviewResponse, ServiceItemResponse, UserResponse } from "../types/api";

const DAY_LABELS = ["Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt"];
const MONTHS_SHORT = ["Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"];

function toIsoDate(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

// Randevu ufku (bkz. CLAUDE.md, en fazla 90 gün ileri) çok geniş -- ekranda
// tek seferde gösterilebilecek, kullanışlı bir pencere olarak 7 gün seçildi
// (AI Studio'daki gün seçici de aynı pencereyi kullanıyor). Daha ileri bir
// tarih istenirse müşteri bugünden itibaren ilerleyip ulaşabilir -- ayrı bir
// "ileri git" oku eklemek şimdilik gerek yok, 90 günlük sınırın tamamını tek
// ekranda sunmak zaten kullanışlı olmazdı.
function buildDayOptions(): { iso: string; label: string; dayNum: number; month: string }[] {
  const today = new Date();
  const options = [];
  for (let i = 0; i < 7; i++) {
    const d = new Date(today);
    d.setDate(d.getDate() + i);
    const label = i === 0 ? "Bugün" : i === 1 ? "Yarın" : DAY_LABELS[d.getDay()];
    options.push({ iso: toIsoDate(d), label, dayNum: d.getDate(), month: MONTHS_SHORT[d.getMonth()] });
  }
  return options;
}

function formatTime(timeStr: string) {
  // "09:45:00" → "09:45"
  return timeStr.slice(0, 5);
}

function formatReviewDate(dateStr: string) {
  const date = new Date(dateStr);
  return `${date.getDate()} ${MONTHS_SHORT[date.getMonth()]} ${date.getFullYear()}`;
}

interface ToastState {
  message: string;
  type: "success" | "error";
}

const DAY_OPTIONS = buildDayOptions();

// 2. Google AI Studio prototipiyle karşılaştırma sonrası (2026-09-04):
// işletme bilgisi + randevu alma akışı tek bir sayfada, AI Studio'nun
// SalonDetailModal + BookingModal ikilisinin birleşimi (bizde zaten ayrı bir
// modal katmanı yok, /business/:id doğrudan bir sayfa). Uzman seçimi
// BİLEREK yok (müşteri personel seçmez/görmez -- CLAUDE.md kararı), ödeme/
// depozito bilgisi BİLEREK yok (kullanıcı onaylı ret). "İletişim Bilgileri"
// bölümü AI Studio'da düzenlenebilir bir formdu ama backend'de (AppointmentRequest)
// isim/telefon/not alanı yok -- kullanıcı onayıyla salt-okunur bir özet karta
// indirgendi, "Özel Not" alanı hiç eklenmedi (ayrı iş kalemi: ROADMAP 3.13).
export default function BusinessDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const [business, setBusiness] = useState<BusinessDetailResponse | null>(null);
  // Eskiden herhangi bir hata (429 rate limit dahil) tek bir "İşletme
  // bulunamadı" mesajına düşüyordu -- kullanıcıya YANLIŞ bilgi veriyordu
  // (gerçek sebep "çok fazla istek attınız" iken "böyle bir işletme yok"
  // deniyordu). Artık gerçek 404 ile diğer hatalar ayrı mesajlarla.
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isFavorited, setIsFavorited] = useState(false);
  const [reviews, setReviews] = useState<ReviewResponse[]>([]);
  const [reviewsLoading, setReviewsLoading] = useState(true);
  const [profile, setProfile] = useState<UserResponse | null>(null);

  const [selectedService, setSelectedService] = useState<ServiceItemResponse | null>(null);
  const [selectedDate, setSelectedDate] = useState(DAY_OPTIONS[0].iso);
  const [slots, setSlots] = useState<string[]>([]);
  const [selectedSlot, setSelectedSlot] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [slotsLoading, setSlotsLoading] = useState(false);
  const [bookingLoading, setBookingLoading] = useState(false);
  const [toast, setToast] = useState<ToastState | null>(null);
  const [imgFailed, setImgFailed] = useState(false);

  useEffect(() => {
    setImgFailed(false);
    setLoading(true);
    setLoadError(null);
    api
      .get<BusinessDetailResponse>(`/api/businesses/${id}`)
      .then((res) => setBusiness(res.data))
      .catch((err) => {
        const message = axios.isAxiosError(err) && err.response?.status === 404
          ? "İşletme bulunamadı."
          : getErrorMessage(err, "İşletme bilgileri yüklenirken bir hata oluştu.");
        setLoadError(message);
      })
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => {
    setReviewsLoading(true);
    api
      .get<ReviewResponse[]>(`/api/reviews/business/${id}`)
      .then((res) => setReviews(res.data))
      .catch(() => {
        // Sessizce yut -- yorumlar sayfanin ana islevi (randevu alma) icin
        // kritik degil, ayri bir hata toast'i gereksiz gurultu olurdu.
      })
      .finally(() => setReviewsLoading(false));
  }, [id]);

  // Favori durumu -- ayrı bir "var mı" ucu yok, /me listesinde arıyoruz.
  useEffect(() => {
    if (!isAuthenticated) {
      setIsFavorited(false);
      return;
    }
    api
      .get<BusinessDetailResponse[]>("/api/favorites/me")
      .then((res) => setIsFavorited(res.data.some((b) => b.id === Number(id))))
      .catch(() => {});
  }, [id, isAuthenticated]);

  // İletişim Bilgileri kartı için gerçek ad/telefon -- backend'e ayrıca
  // gönderilmiyor (kimlik zaten token'dan geliyor), sadece "bu randevu kime
  // ait" özetini göstermek için.
  useEffect(() => {
    if (!isAuthenticated) {
      setProfile(null);
      return;
    }
    api
      .get<UserResponse>("/api/users/me")
      .then((res) => setProfile(res.data))
      .catch(() => setProfile(null));
  }, [isAuthenticated]);

  async function toggleFavorite() {
    const wasFavorited = isFavorited;
    setIsFavorited(!wasFavorited);
    try {
      if (wasFavorited) {
        await api.delete(`/api/favorites/${id}`);
      } else {
        await api.post(`/api/favorites/${id}`);
      }
    } catch {
      setIsFavorited(wasFavorited);
    }
  }

  // Hizmet ya da tarih değiştiğinde müsait saatleri otomatik çek -- AI
  // Studio'daki akışta ayrı bir "getir" butonu yok, bir gün/hizmete
  // tıklamak yeterli.
  useEffect(() => {
    if (!selectedService || !selectedDate) {
      setSlots([]);
      return;
    }
    let cancelled = false;
    setSlotsLoading(true);
    setSelectedSlot(null);
    api
      .get<string[]>("/api/appointments/available-slots", {
        params: { businessId: id, serviceId: selectedService.id, date: selectedDate },
      })
      .then((res) => {
        if (!cancelled) setSlots(res.data);
      })
      .catch((err) => {
        if (!cancelled) {
          setSlots([]);
          setToast({ message: getErrorMessage(err, "Saatler alınırken hata oluştu."), type: "error" });
        }
      })
      .finally(() => {
        if (!cancelled) setSlotsLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedService?.id, selectedDate, id]);

  async function handleBooking() {
    if (!selectedSlot || !selectedService) return;

    setBookingLoading(true);
    try {
      const body: AppointmentRequest = {
        businessId: Number(id),
        serviceId: selectedService.id,
        appointmentDate: `${selectedDate}T${selectedSlot}`,
      };
      await api.post("/api/appointments/create", body);
      setToast({ message: "🎉 Randevunuz başarıyla oluşturuldu!", type: "success" });
      setTimeout(() => navigate("/appointments"), 1200);
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Randevu oluşturulurken hata oluştu."), type: "error" });
      setBookingLoading(false);
    }
  }

  const selectedDay = useMemo(() => DAY_OPTIONS.find((d) => d.iso === selectedDate), [selectedDate]);

  if (loading) {
    return (
      <div className="flex justify-center items-center min-h-[60vh]">
        <div className="flex items-center gap-3 text-slate-400">
          <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          Yükleniyor...
        </div>
      </div>
    );
  }

  if (loadError || !business) {
    return (
      <div className="text-center py-20">
        <p className="text-slate-400 text-lg">{loadError ?? "İşletme bulunamadı."}</p>
        <button onClick={() => navigate("/")} className="mt-4 text-brand hover:underline text-sm cursor-pointer">
          ← Ana Sayfaya Dön
        </button>
      </div>
    );
  }

  const { Icon } = getCategory(business.category);
  const coverUrl = imgFailed ? null : resolvePhotoUrl(business.coverPhotoDetailUrl);
  const hasRating = business.reviewCount > 0 && business.averageRating != null;

  return (
    <div className="w-full min-h-[calc(100vh-4.5rem)] bg-slate-100/70 py-4 sm:py-8 px-3 sm:px-6 lg:px-8 flex flex-col items-center">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      <div className="w-full max-w-6xl">
        <div className="flex items-center justify-between mb-4 px-1">
          <button
            onClick={() => navigate("/")}
            className="inline-flex items-center gap-2 text-xs sm:text-sm font-bold text-slate-700 hover:text-slate-950 transition bg-white px-4 py-2 rounded-xl border border-slate-200/80 shadow-2xs hover:shadow-xs cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4 text-brand" />
            <span>Geri Dön / Salonları Keşfet</span>
          </button>
          <span className="text-xs text-slate-500 font-medium hidden sm:inline">Randevum • Kolay Randevu Alma</span>
        </div>

        {/* Header banner */}
        <div className="relative rounded-2xl sm:rounded-3xl overflow-hidden shadow-xl border border-white/10 mb-6 min-h-[190px] flex flex-col justify-end bg-gradient-to-br from-brand via-brand-mid to-brand-glow">
          {coverUrl ? (
            <img
              src={coverUrl}
              alt={business.name}
              onError={() => setImgFailed(true)}
              className="absolute inset-0 w-full h-full object-cover"
            />
          ) : (
            <span className="absolute inset-0 flex items-center justify-center text-white/15 scale-[3.2]">
              <Icon />
            </span>
          )}
          <div className="absolute inset-0 bg-gradient-to-t from-[#081120]/90 via-[#081120]/40 to-transparent" />

          <div className="relative p-5 sm:p-7 flex items-end justify-between gap-4">
            <div className="min-w-0">
              <div className="flex items-center gap-1.5 flex-wrap mb-2">
                <span className="text-[10px] font-bold text-white bg-white/15 backdrop-blur-sm px-2.5 py-1 rounded-full uppercase tracking-wide">
                  {getCategoryLabel(business.category)}
                </span>
                <span className="text-[10px] font-semibold text-white/90 bg-white/10 backdrop-blur-sm px-2.5 py-1 rounded-full">
                  {getGenderLabel(business.servedGender)}
                </span>
                {hasRating && (
                  <span className="flex items-center gap-1 text-[11px] font-bold text-white bg-white/15 backdrop-blur-sm px-2.5 py-1 rounded-full">
                    <Star className="w-3 h-3 text-amber-400 fill-amber-400" />
                    {business.averageRating!.toFixed(1)}
                    <span className="text-white/60 font-normal">({business.reviewCount})</span>
                  </span>
                )}
              </div>
              <h1 className="text-xl sm:text-3xl font-black text-white tracking-tight flex items-center gap-2 truncate">
                <span className="truncate">{business.name}</span>
                {business.verified && <BadgeCheck className="w-5 h-5 sm:w-6 sm:h-6 text-sky-400 shrink-0" aria-label="Onaylı işletme" />}
              </h1>
              {business.address && (
                <p className="text-xs sm:text-sm text-blue-100/90 mt-1.5 flex items-center gap-1.5">
                  <MapPin className="w-3.5 h-3.5 shrink-0" />
                  <span className="truncate">{business.address}</span>
                </p>
              )}
            </div>

            <div className="flex flex-col items-end gap-2 shrink-0">
              {isAuthenticated && (
                <button
                  onClick={toggleFavorite}
                  title={isFavorited ? "Favorilerden çıkar" : "Favorilere ekle"}
                  className={`w-9 h-9 rounded-full backdrop-blur-sm flex items-center justify-center transition-all cursor-pointer ${
                    isFavorited ? "bg-white text-rose-500" : "bg-white/15 hover:bg-white/25 text-white"
                  }`}
                >
                  <Heart className="w-4 h-4" fill={isFavorited ? "currentColor" : "none"} />
                </button>
              )}
              <div className="text-right bg-white/10 backdrop-blur-sm border border-white/15 rounded-xl px-3 py-2">
                <p className="text-[10px] font-semibold text-blue-100/70 uppercase tracking-wide">Randevu Durumu</p>
                <p
                  className={`text-xs sm:text-sm font-bold flex items-center gap-1 justify-end ${
                    business.autoApprove ? "text-emerald-400" : "text-amber-300"
                  }`}
                >
                  <span className={`w-1.5 h-1.5 rounded-full ${business.autoApprove ? "bg-emerald-400" : "bg-amber-300"}`} />
                  {business.autoApprove ? "Anında Onaylı" : "İşletme Onayından Geçer"}
                </p>
              </div>
            </div>
          </div>
        </div>

        {business.description && (
          <p className="text-sm text-slate-600 mb-6 px-1 max-w-3xl">{business.description}</p>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-5 sm:gap-6">
          {/* Sol: hizmet + tarih/saat + yorumlar */}
          <div className="lg:col-span-2 space-y-5 sm:space-y-6">
            {/* 1. Hizmet Seçin */}
            <div className="bg-white rounded-2xl sm:rounded-3xl border border-slate-200/90 shadow-xs p-5 sm:p-6">
              <div className="flex items-center justify-between mb-1">
                <h2 className="text-sm sm:text-base font-extrabold text-slate-900 flex items-center gap-2">
                  <span className="w-6 h-6 rounded-full bg-brand text-white text-xs font-bold flex items-center justify-center shrink-0">
                    1
                  </span>
                  Hizmet Seçin
                </h2>
                <span className="text-[11px] text-slate-400 font-medium">{business.serviceItems.length} Hizmet</span>
              </div>
              <p className="text-xs text-slate-500 mb-4 pl-8">Almak istediğiniz işlemi listeden belirleyin</p>

              {business.serviceItems.length === 0 ? (
                <p className="text-sm text-slate-500 italic pl-8">Bu işletmeye ait hizmet bilgisi bulunamadı.</p>
              ) : (
                <div className="space-y-2.5">
                  {business.serviceItems.map((svc) => {
                    const active = selectedService?.id === svc.id;
                    return (
                      <button
                        key={svc.id}
                        onClick={() => setSelectedService(svc)}
                        className={`w-full flex items-center gap-3 p-3.5 sm:p-4 rounded-2xl border text-left transition cursor-pointer ${
                          active ? "border-brand bg-blue-50/60 shadow-xs" : "border-slate-200 hover:border-slate-300 hover:bg-slate-50/70"
                        }`}
                      >
                        <span
                          className={`w-5 h-5 rounded-full border flex items-center justify-center shrink-0 ${
                            active ? "bg-brand border-brand text-white" : "border-slate-300"
                          }`}
                        >
                          {active && <Check className="w-3.5 h-3.5" />}
                        </span>
                        <span className="flex-1 min-w-0">
                          <span className="flex items-center gap-2 flex-wrap">
                            <span className="font-bold text-sm text-slate-900">{svc.name}</span>
                            <span className="text-[11px] text-slate-400 font-medium">{svc.durationInMinutes} dk</span>
                          </span>
                          {svc.description && (
                            <span className="block text-xs text-slate-500 mt-0.5 line-clamp-1">{svc.description}</span>
                          )}
                        </span>
                        <span className="font-black text-sm sm:text-base text-slate-900 shrink-0">₺{svc.price}</span>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            {/* 2. Tarih & Saat Belirleyin */}
            <div className="bg-white rounded-2xl sm:rounded-3xl border border-slate-200/90 shadow-xs p-5 sm:p-6">
              <h2 className="text-sm sm:text-base font-extrabold text-slate-900 flex items-center gap-2 mb-1">
                <span className="w-6 h-6 rounded-full bg-brand text-white text-xs font-bold flex items-center justify-center shrink-0">
                  2
                </span>
                Tarih & Saat Belirleyin
              </h2>
              <p className="text-xs text-slate-500 mb-4 pl-8">Size en uygun gün ve saat dilimini seçin</p>

              <div className="pl-8">
                <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2">Gün Seçimi</p>
                <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none">
                  {DAY_OPTIONS.map((day) => {
                    const active = selectedDate === day.iso;
                    return (
                      <button
                        key={day.iso}
                        onClick={() => setSelectedDate(day.iso)}
                        className={`shrink-0 flex flex-col items-center px-3.5 py-2 rounded-xl border text-xs font-semibold transition cursor-pointer ${
                          active
                            ? "bg-[#0d2137] text-white border-[#0d2137] shadow-sm"
                            : "border-slate-200 text-slate-600 hover:border-slate-300 hover:bg-slate-50"
                        }`}
                      >
                        <span>{day.label}</span>
                        <span className={`text-[11px] font-normal ${active ? "text-blue-200" : "text-slate-400"}`}>
                          {day.dayNum} {day.month}
                        </span>
                      </button>
                    );
                  })}
                </div>

                <div className="flex items-center justify-between mt-5 mb-2">
                  <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">
                    Müsait Saatler {selectedDay && `(${selectedDay.dayNum} ${selectedDay.month})`}
                  </p>
                  {!slotsLoading && selectedService && slots.length > 0 && (
                    <span className="flex items-center gap-1 text-[11px] font-semibold text-emerald-600">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                      {slots.length} uygun saat
                    </span>
                  )}
                </div>

                {!selectedService ? (
                  <p className="text-sm text-slate-400 italic py-3">Önce yukarıdan bir hizmet seçin.</p>
                ) : slotsLoading ? (
                  <div className="flex items-center gap-2 text-sm text-slate-400 py-3">
                    <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                    Saatler yükleniyor...
                  </div>
                ) : slots.length === 0 ? (
                  <p className="text-sm text-slate-400 italic py-3">Bu tarihte uygun saat yok, başka bir gün deneyin.</p>
                ) : (
                  <div className="grid grid-cols-3 sm:grid-cols-4 gap-2.5">
                    {slots.map((slot) => {
                      const active = selectedSlot === slot;
                      return (
                        <button
                          key={slot}
                          onClick={() => setSelectedSlot(slot)}
                          className={`py-2.5 rounded-xl text-sm font-bold transition cursor-pointer ${
                            active
                              ? "bg-brand text-white shadow-sm"
                              : "bg-slate-50 border border-slate-200 text-slate-700 hover:border-slate-300 hover:bg-slate-100"
                          }`}
                        >
                          {formatTime(slot)}
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>

            {business.latitude != null && business.longitude != null && (
              <div className="bg-white rounded-2xl sm:rounded-3xl border border-slate-200/90 shadow-xs p-5 sm:p-6">
                <h2 className="text-sm sm:text-base font-extrabold text-slate-900 mb-3">Konum</h2>
                <LocationPicker latitude={business.latitude} longitude={business.longitude} readOnly height={200} />
              </div>
            )}

            {/* Yorumlar */}
            <div className="bg-white rounded-2xl sm:rounded-3xl border border-slate-200/90 shadow-xs p-5 sm:p-6">
              <h2 className="text-sm sm:text-base font-extrabold text-slate-900 mb-4">Değerlendirmeler</h2>
              {reviewsLoading ? (
                <div className="flex justify-center py-6">
                  <svg className="animate-spin h-4 w-4 text-slate-400" viewBox="0 0 24 24" fill="none">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                </div>
              ) : reviews.length === 0 ? (
                <p className="text-sm text-slate-400 text-center py-4 italic">Bu işletme için henüz değerlendirme yapılmamış.</p>
              ) : (
                <div className="space-y-4">
                  {reviews.map((review) => (
                    <div key={review.id} className="border-b border-slate-100 last:border-0 pb-4 last:pb-0">
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-sm font-bold text-slate-900">
                          {review.reviewer.name} {review.reviewer.surName?.charAt(0)}.
                        </span>
                        <span className="text-xs text-slate-400">{formatReviewDate(review.createdAt)}</span>
                      </div>
                      <StarRating value={review.rating} size="text-xs" />
                      {review.comment && <p className="text-sm text-slate-600 mt-1.5">{review.comment}</p>}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Sağ: iletişim özeti + randevu özeti (sticky) */}
          <div className="lg:col-span-1">
            <div className="lg:sticky lg:top-24 space-y-5">
              {isAuthenticated && profile && (
                <div className="bg-white rounded-2xl sm:rounded-3xl border border-slate-200/90 shadow-xs p-5 sm:p-6">
                  <h2 className="text-sm sm:text-base font-extrabold text-slate-900 flex items-center gap-2 mb-1">
                    <span className="w-6 h-6 rounded-full bg-brand text-white text-xs font-bold flex items-center justify-center shrink-0">
                      3
                    </span>
                    İletişim Bilgileri
                  </h2>
                  <p className="text-xs text-slate-500 mb-4 pl-8">Randevu teyidi bu bilgilerle eşleşecek</p>
                  <div className="space-y-2.5 pl-8">
                    <div className="flex items-center gap-2 text-sm text-slate-700">
                      <span className="w-7 h-7 rounded-lg bg-slate-100 flex items-center justify-center shrink-0">
                        <BadgeCheck className="w-3.5 h-3.5 text-slate-500" />
                      </span>
                      <span className="font-semibold truncate">{profile.name} {profile.surName}</span>
                    </div>
                    {profile.phone && (
                      <div className="flex items-center gap-2 text-sm text-slate-700">
                        <span className="w-7 h-7 rounded-lg bg-slate-100 flex items-center justify-center shrink-0">
                          <Phone className="w-3.5 h-3.5 text-slate-500" />
                        </span>
                        <span className="font-semibold">{profile.phone}</span>
                      </div>
                    )}
                  </div>
                </div>
              )}

              <div className="bg-white rounded-2xl sm:rounded-3xl border border-slate-200/90 shadow-xs p-5 sm:p-6">
                <h2 className="text-sm sm:text-base font-extrabold text-slate-900 mb-4">Randevu Özeti</h2>
                <div className="space-y-2.5 text-xs sm:text-sm mb-4">
                  <div className="flex justify-between items-center py-1.5 border-b border-slate-100">
                    <span className="text-slate-500">Seçilen Salon</span>
                    <span className="font-bold text-slate-900 text-right truncate max-w-[55%]">{business.name}</span>
                  </div>
                  <div className="flex justify-between items-center py-1.5 border-b border-slate-100">
                    <span className="text-slate-500">İşlem</span>
                    <span className="font-bold text-slate-900 text-right truncate max-w-[55%]">
                      {selectedService?.name ?? "—"}
                    </span>
                  </div>
                  <div className="flex justify-between items-center py-1.5 border-b border-slate-100">
                    <span className="text-slate-500">İşlem Süresi</span>
                    <span className="font-bold text-slate-900">
                      {selectedService ? `${selectedService.durationInMinutes} dakika` : "—"}
                    </span>
                  </div>
                  <div className="flex justify-between items-center py-1.5">
                    <span className="text-slate-500 flex items-center gap-1">
                      <Clock className="w-3.5 h-3.5" /> Tarih & Saat
                    </span>
                    <span className="font-bold text-slate-900">
                      {selectedSlot && selectedDay
                        ? `${selectedDay.dayNum} ${selectedDay.month} • ${formatTime(selectedSlot)}`
                        : "—"}
                    </span>
                  </div>
                </div>

                <div className="flex items-center justify-between pt-3 border-t border-slate-200 mb-4">
                  <span className="text-sm font-bold text-slate-700">Toplam Tutar</span>
                  <span className="text-xl font-black text-slate-900">
                    {selectedService ? `₺${selectedService.price}` : "—"}
                  </span>
                </div>

                <button
                  onClick={handleBooking}
                  disabled={!selectedSlot || bookingLoading}
                  className="w-full py-3 flex items-center justify-center gap-2 bg-[#0d2137] hover:bg-brand-hover text-white font-bold text-sm rounded-xl transition disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
                >
                  <CheckCircle2 className="w-4 h-4" />
                  {bookingLoading ? "Gönderiliyor..." : "Randevuyu Onayla"}
                </button>
                <p className="text-[11px] text-slate-400 text-center mt-2.5">
                  {business.autoApprove
                    ? "Talebiniz gönderildiğinde anında onaylanır."
                    : "İşletme talebinizi inceleyip onaylayacak; belirtilen sürede yanıtlanmazsa talep otomatik olarak düşer."}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
