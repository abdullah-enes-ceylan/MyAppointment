import { useState, useEffect, useMemo, type ChangeEvent, type FormEvent, type ReactNode } from "react";
import {
  AlertCircle,
  ArrowLeft,
  Calendar,
  Check,
  CheckCircle2,
  ChevronRight,
  Clock,
  MapPin,
  Navigation,
  Search,
  X,
} from "lucide-react";
import api from "../api/axios";
import { getErrorMessage } from "../api/errors";
import Toast from "../components/Toast";
import StarRating from "../components/StarRating";
import { resolvePhotoUrl } from "../utils/photo";
import type { AppointmentResponse, AppointmentStatus, BusinessDetailResponse, ReviewRequest } from "../types/api";

const MONTHS_SHORT = ["Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"];
const DAYS_SHORT = ["Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt"];

function formatDateTime(dateStr: string) {
  const date = new Date(dateStr);
  const months = [
    "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
    "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık",
  ];
  const day = String(date.getDate()).padStart(2, "0");
  const month = months[date.getMonth()];
  const year = date.getFullYear();
  const hours = String(date.getHours()).padStart(2, "0");
  const minutes = String(date.getMinutes()).padStart(2, "0");
  return `${day} ${month} ${year} — ${hours}:${minutes}`;
}

function calendarParts(dateStr: string) {
  const d = new Date(dateStr);
  return { dayName: DAYS_SHORT[d.getDay()], dayNum: String(d.getDate()), monthName: MONTHS_SHORT[d.getMonth()] };
}

function timeRange(apt: AppointmentResponse) {
  const start = new Date(apt.appointmentDate);
  const end = new Date(start.getTime() + apt.serviceItem.durationInMinutes * 60000);
  const fmt = (d: Date) => `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
  return `${fmt(start)} - ${fmt(end)}`;
}

// Google Maps/Takvim linkleri sadece isletme ADI ile kuruluyor -- AppointmentResponse.
// business bilerek kucuk (BusinessSummary: id/name/suspended), adres tasimiyor.
// Tam adres icin detay ekraninda ayrica GET /api/businesses/{id} cekiliyor,
// o an gercek adres varsa link de onu kullanir.
function openInMaps(query: string) {
  window.open(`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`, "_blank");
}

function addToGoogleCalendar(apt: AppointmentResponse, location: string) {
  const start = new Date(apt.appointmentDate);
  const end = new Date(start.getTime() + apt.serviceItem.durationInMinutes * 60000);
  const fmt = (d: Date) => d.toISOString().replace(/[-:]|\.\d{3}/g, "");
  const title = encodeURIComponent(`${apt.serviceItem.name} - ${apt.business.name}`);
  const details = encodeURIComponent(`Randevum randevusu: ${apt.serviceItem.name}`);
  const url = `https://calendar.google.com/calendar/render?action=TEMPLATE&text=${title}&dates=${fmt(start)}/${fmt(
    end
  )}&details=${details}&location=${encodeURIComponent(location)}`;
  window.open(url, "_blank");
}

interface StatusMeta {
  label: string;
  icon: ReactNode;
  classes: string;
}

const STATUS_CONFIG: Record<AppointmentStatus, StatusMeta> = {
  PENDING: { label: "Onay Bekliyor", icon: <Clock className="w-3.5 h-3.5" />, classes: "bg-amber-50 text-amber-700 border-amber-200" },
  APPROVED: { label: "Onaylandı", icon: <CheckCircle2 className="w-3.5 h-3.5" />, classes: "bg-emerald-50 text-emerald-700 border-emerald-200" },
  REJECTED: { label: "Reddedildi", icon: <AlertCircle className="w-3.5 h-3.5" />, classes: "bg-red-50 text-red-700 border-red-200" },
  CANCELLED: { label: "İptal Edildi", icon: <AlertCircle className="w-3.5 h-3.5" />, classes: "bg-rose-50 text-rose-700 border-rose-200" },
  COMPLETED: { label: "Tamamlandı", icon: <Check className="w-3.5 h-3.5" />, classes: "bg-slate-100 text-slate-700 border-slate-200" },
  NO_SHOW: { label: "Gelinmedi", icon: <AlertCircle className="w-3.5 h-3.5" />, classes: "bg-orange-50 text-orange-700 border-orange-200" },
  // REJECTED'dan bilerek farklı renk: burada işletme talebi reddetmedi,
  // süresinde yanıtlamadı (bkz. backend AppointmentStatus.EXPIRED yorumu).
  EXPIRED: { label: "Zaman Aşımına Uğradı", icon: <Clock className="w-3.5 h-3.5" />, classes: "bg-purple-50 text-purple-700 border-purple-200" },
};

// Randevu iptali sadece PENDING/APPROVED durumundaki randevular için
// AppointmentService.changeStatus'ta izin veriliyor (bkz. backend).
const CANCELLABLE_STATUSES: AppointmentStatus[] = ["PENDING", "APPROVED"];

type TabType = "upcoming" | "past" | "cancelled";

// 2. Google AI Studio prototipiyle karşılaştırma sonrası (2026-09-04):
// durum bazlı 3 sekme (eskiden tarih bazlı 2 bölümdü). Gerçek 7 statünün
// TAMAMI bir sekmeye düşüyor, hiçbiri sessizce kaybolmuyor:
// - Yaklaşanlar: PENDING + APPROVED (bkz. AppointmentStatus.ACTIVE_STATUSES
//   ile aynı tanım, backend'deki tek kaynakla tutarlı).
// - Geçmiş: COMPLETED + NO_SHOW (randevu SAATİ geçmiş VE bir sonuca bağlanmış).
// - İptal Edilenler: CANCELLED + REJECTED + EXPIRED (randevu hiç gerçekleşmeden
//   sona ermiş, üç farklı sebeple).
function tabForStatus(status: AppointmentStatus): TabType {
  if (status === "PENDING" || status === "APPROVED") return "upcoming";
  if (status === "COMPLETED" || status === "NO_SHOW") return "past";
  return "cancelled";
}

interface ReviewFormProps {
  appointmentId: number;
  onSubmitted: () => void;
  onCancel: () => void;
}

// Sadece COMPLETED randevularda gösterilir -- backend zaten bunu ZORUNLU
// kılıyor (ReviewService.createReview, status != COMPLETED ise 409).
function ReviewForm({ appointmentId, onSubmitted, onCancel }: ReviewFormProps) {
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const body: ReviewRequest = { appointmentId, rating, comment: comment || null };
      await api.post("/api/reviews/create", body);
      onSubmitted();
    } catch (err) {
      setError(getErrorMessage(err, "Yorum gönderilirken hata oluştu."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3 bg-slate-50 p-4 rounded-2xl border border-slate-200">
      <div className="flex items-center gap-3">
        <span className="text-sm text-slate-600">Puanınız:</span>
        <StarRating value={rating} onChange={setRating} interactive size="text-2xl" />
      </div>
      <textarea
        value={comment}
        onChange={(e: ChangeEvent<HTMLTextAreaElement>) => setComment(e.target.value)}
        maxLength={1000}
        rows={3}
        placeholder="Deneyiminizi paylaşın (opsiyonel)"
        className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-slate-900 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand/30 resize-none"
      />
      {error && <p className="text-xs text-red-600">{error}</p>}
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={saving}
          className="px-4 py-2 text-xs sm:text-sm font-bold text-white bg-brand hover:bg-brand-hover rounded-xl disabled:opacity-50 transition cursor-pointer"
        >
          {saving ? "Gönderiliyor..." : "Yorumu Gönder"}
        </button>
        <button
          type="button"
          onClick={onCancel}
          disabled={saving}
          className="px-4 py-2 text-xs sm:text-sm font-medium text-slate-600 hover:text-slate-900 border border-slate-200 rounded-xl disabled:opacity-50 transition cursor-pointer"
        >
          Vazgeç
        </button>
      </div>
    </form>
  );
}

interface ToastState {
  message: string;
  type: "success" | "error";
}

// Müşterinin kendi randevu geçmişi — /appointments/me tüm durumdaki
// randevuları döner (backend kimliği token'dan alıyor, path'te id yok,
// bu yüzden başka bir kullanıcının randevusu asla görünmez).
export default function MyAppointmentsPage() {
  const [appointments, setAppointments] = useState<AppointmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<ToastState | null>(null);
  const [cancelLoading, setCancelLoading] = useState<number | null>(null);
  const [activeTab, setActiveTab] = useState<TabType>("upcoming");
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [reviewing, setReviewing] = useState(false);
  const [cancelConfirm, setCancelConfirm] = useState(false);

  // Detay ekranı için işletmenin tam bilgisi (adres/fotoğraf/puan) --
  // AppointmentResponse.business bilerek küçük (BusinessSummary), bu yüzden
  // sadece bir randevu açıldığında, tek seferlik ayrıca çekiliyor.
  const [detailBusiness, setDetailBusiness] = useState<BusinessDetailResponse | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  useEffect(() => {
    fetchAppointments();
  }, []);

  async function fetchAppointments() {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get<AppointmentResponse[]>("/api/appointments/me");
      setAppointments(res.data);
    } catch {
      setError("Randevularınız yüklenirken hata oluştu.");
    } finally {
      setLoading(false);
    }
  }

  const selected = appointments.find((a) => a.id === selectedId) ?? null;

  useEffect(() => {
    if (!selected) {
      setDetailBusiness(null);
      return;
    }
    let cancelled = false;
    setDetailLoading(true);
    api
      .get<BusinessDetailResponse>(`/api/businesses/${selected.business.id}`)
      .then((res) => {
        if (!cancelled) setDetailBusiness(res.data);
      })
      .catch(() => {
        if (!cancelled) setDetailBusiness(null);
      })
      .finally(() => {
        if (!cancelled) setDetailLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId]);

  function handleReviewSubmitted(appointmentId: number) {
    setAppointments((prev) => prev.map((a) => (a.id === appointmentId ? { ...a, hasReview: true } : a)));
    setReviewing(false);
    setToast({ message: "Yorumunuz kaydedildi, teşekkürler!", type: "success" });
  }

  async function handleCancel(appointmentId: number) {
    setCancelLoading(appointmentId);
    try {
      const res = await api.put<AppointmentResponse>(`/api/appointments/${appointmentId}/cancel`);
      setAppointments((prev) => prev.map((a) => (a.id === appointmentId ? res.data : a)));
      setToast({ message: "Randevu iptal edildi.", type: "success" });
      setCancelConfirm(false);
    } catch (err) {
      setToast({ message: getErrorMessage(err, "İptal sırasında bir hata oluştu."), type: "error" });
    } finally {
      setCancelLoading(null);
    }
  }

  const filtered = useMemo(() => {
    const q = searchQuery.trim().toLocaleLowerCase("tr");
    return appointments
      .filter((a) => tabForStatus(a.status) === activeTab)
      .filter((a) => {
        if (!q) return true;
        return [a.business.name, a.serviceItem.name].some((f) => f.toLocaleLowerCase("tr").includes(q));
      })
      .sort((a, b) => new Date(b.appointmentDate).getTime() - new Date(a.appointmentDate).getTime());
  }, [appointments, activeTab, searchQuery]);

  const counts = useMemo(() => {
    const acc: Record<TabType, number> = { upcoming: 0, past: 0, cancelled: 0 };
    appointments.forEach((a) => acc[tabForStatus(a.status)]++);
    return acc;
  }, [appointments]);

  if (loading) {
    return (
      <div className="flex justify-center py-24">
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

  if (error) {
    return (
      <div className="text-center py-16">
        <p className="text-red-500 text-lg mb-4">{error}</p>
        <button onClick={fetchAppointments} className="text-brand hover:underline text-sm font-medium cursor-pointer">
          Tekrar Dene
        </button>
      </div>
    );
  }

  // ---------------------------------------------------------------
  // DETAIL VIEW
  // ---------------------------------------------------------------
  if (selected) {
    const config = STATUS_CONFIG[selected.status];
    const businessName = detailBusiness?.name ?? selected.business.name;
    const address = detailBusiness?.address;
    const mapsQuery = address ? `${businessName} ${address}` : businessName;
    const coverUrl = resolvePhotoUrl(detailBusiness?.coverPhotoDetailUrl ?? null);

    return (
      <div className="w-full min-h-[calc(100vh-4.5rem)] bg-slate-100/70 py-4 sm:py-8 px-3 sm:px-4 flex flex-col items-center">
        {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
        <div className="w-full max-w-xl bg-white rounded-2xl sm:rounded-3xl shadow-xl overflow-hidden border border-slate-200/80">
          <div className="bg-[#0b1b2d] px-5 py-4 text-white border-b border-white/10 flex items-center justify-between">
            <div className="flex items-center gap-3 min-w-0">
              <button
                onClick={() => {
                  setSelectedId(null);
                  setReviewing(false);
                  setCancelConfirm(false);
                }}
                className="p-1.5 rounded-xl bg-white/10 hover:bg-white/20 text-white transition cursor-pointer shrink-0"
              >
                <ArrowLeft className="w-5 h-5" />
              </button>
              <div className="min-w-0">
                <h3 className="font-extrabold text-sm sm:text-base text-white truncate">
                  {businessName} • {selected.serviceItem.name}
                </h3>
                <p className="text-[11px] text-blue-200/80 flex items-center gap-1">
                  <Clock className="w-3 h-3 text-sky-400 shrink-0" />
                  <span className="truncate">{formatDateTime(selected.appointmentDate)}</span>
                </p>
              </div>
            </div>
          </div>

          <div className="p-4 sm:p-6 space-y-5 bg-slate-50/50">
            <div className="relative w-full h-40 sm:h-48 rounded-2xl overflow-hidden shadow-sm border border-slate-200 bg-gradient-to-br from-brand via-brand-mid to-brand-glow">
              {coverUrl && <img src={coverUrl} alt={businessName} className="w-full h-full object-cover" />}
              <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-black/10 to-transparent" />
              <div className="absolute bottom-3 left-3.5 right-3.5 text-white">
                <span
                  className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold border mb-1 bg-white/15 backdrop-blur-md border-white/25`}
                >
                  {config.icon}
                  {config.label}
                </span>
                <h2 className="text-lg font-black tracking-tight drop-shadow-sm truncate">{businessName}</h2>
              </div>
            </div>

            {(address || detailBusiness) && !detailLoading && (
              <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs flex items-center justify-between gap-3">
                <div className="min-w-0">
                  {detailBusiness?.averageRating != null && (
                    <p className="text-xs text-slate-500">
                      ★ {detailBusiness.averageRating.toFixed(1)} ({detailBusiness.reviewCount} yorum)
                    </p>
                  )}
                  {address && <p className="text-xs text-slate-600 mt-0.5 truncate">{address}</p>}
                </div>
                <button
                  onClick={() => openInMaps(mapsQuery)}
                  className="p-2.5 rounded-xl bg-blue-50 text-brand hover:bg-blue-100 transition shrink-0"
                  title="Haritada Gör"
                >
                  <Navigation className="w-4 h-4" />
                </button>
              </div>
            )}

            <div className="bg-white p-5 rounded-2xl sm:rounded-3xl border border-slate-200/80 shadow-xs space-y-3">
              <h4 className="text-xs font-bold uppercase tracking-wider text-slate-400">Randevu Bilgileri</h4>
              <div className="space-y-2.5 text-xs sm:text-sm">
                <div className="flex justify-between items-center py-1.5 border-b border-slate-100">
                  <span className="text-slate-500">Hizmet</span>
                  <span className="font-bold text-slate-900">{selected.serviceItem.name}</span>
                </div>
                <div className="flex justify-between items-center py-1.5 border-b border-slate-100">
                  <span className="text-slate-500">Tarih & Saat</span>
                  <span className="font-bold text-slate-900">{timeRange(selected)}</span>
                </div>
                {selected.staff && (
                  <div className="flex justify-between items-center py-1.5 border-b border-slate-100">
                    <span className="text-slate-500">Uzman</span>
                    <span className="font-bold text-slate-900">{selected.staff.name}</span>
                  </div>
                )}
                <div className="flex justify-between items-center py-1.5">
                  <span className="text-slate-500">Fiyat</span>
                  <span className="font-black text-slate-900 text-sm sm:text-base">
                    ₺{selected.serviceItem.price}
                  </span>
                </div>
              </div>
            </div>

            {selected.status === "PENDING" && selected.expiresAt && (
              <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-xl px-3.5 py-2.5">
                {formatDateTime(selected.expiresAt)}'e kadar yanıtlanmazsa bu talep otomatik düşer.
              </p>
            )}

            {CANCELLABLE_STATUSES.includes(selected.status) && (
              <div className="bg-white p-5 rounded-2xl sm:rounded-3xl border border-slate-200/80 shadow-xs space-y-2.5">
                <button
                  onClick={() => openInMaps(mapsQuery)}
                  className="w-full py-2.5 px-4 rounded-xl bg-white hover:bg-slate-50 text-slate-800 font-bold text-xs sm:text-sm border border-slate-200 transition shadow-xs flex items-center justify-center gap-2 cursor-pointer"
                >
                  <Navigation className="w-4 h-4 text-brand" />
                  <span>Google Maps ile Aç</span>
                </button>

                <button
                  onClick={() => addToGoogleCalendar(selected, address ?? businessName)}
                  className="w-full py-2.5 px-4 rounded-xl bg-white hover:bg-slate-50 text-slate-800 font-bold text-xs sm:text-sm border border-slate-200 transition shadow-xs flex items-center justify-center gap-2 cursor-pointer"
                >
                  <Calendar className="w-4 h-4 text-brand" />
                  <span>Takvime Ekle</span>
                </button>

                {!cancelConfirm ? (
                  <button
                    onClick={() => setCancelConfirm(true)}
                    className="w-full py-2.5 px-4 rounded-xl bg-white hover:bg-rose-50 text-rose-600 font-semibold text-xs sm:text-sm border border-rose-200 transition shadow-xs flex items-center justify-center gap-2 cursor-pointer"
                  >
                    <AlertCircle className="w-4 h-4" />
                    <span>Randevuyu İptal Et</span>
                  </button>
                ) : (
                  <div className="pt-2 border-t border-slate-100 space-y-2">
                    <p className="text-xs text-slate-500 text-center">Emin misiniz? Bu işlem geri alınamaz.</p>
                    <div className="grid grid-cols-2 gap-2">
                      <button
                        onClick={() => setCancelConfirm(false)}
                        disabled={cancelLoading === selected.id}
                        className="py-2.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold text-xs transition cursor-pointer disabled:opacity-50"
                      >
                        Vazgeç
                      </button>
                      <button
                        onClick={() => handleCancel(selected.id)}
                        disabled={cancelLoading === selected.id}
                        className="py-2.5 rounded-xl bg-rose-600 hover:bg-rose-700 text-white font-bold text-xs transition shadow-sm cursor-pointer disabled:opacity-50"
                      >
                        {cancelLoading === selected.id ? "İptal ediliyor..." : "Evet, iptal et"}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}

            {selected.status === "COMPLETED" && !selected.hasReview && (
              <div className="bg-white p-5 rounded-2xl sm:rounded-3xl border border-slate-200/80 shadow-xs space-y-3">
                {!reviewing ? (
                  <button
                    onClick={() => setReviewing(true)}
                    className="w-full py-2.5 px-4 rounded-xl bg-amber-50 hover:bg-amber-100 text-amber-700 font-bold text-xs sm:text-sm border border-amber-200 transition cursor-pointer"
                  >
                    ⭐ Yorum Yap
                  </button>
                ) : (
                  <ReviewForm
                    appointmentId={selected.id}
                    onCancel={() => setReviewing(false)}
                    onSubmitted={() => handleReviewSubmitted(selected.id)}
                  />
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    );
  }

  // ---------------------------------------------------------------
  // LIST VIEW
  // ---------------------------------------------------------------
  return (
    <div className="w-full min-h-[calc(100vh-4.5rem)] bg-slate-100/70 py-4 sm:py-8 px-2 sm:px-4 flex flex-col items-center">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      <div className="relative w-full max-w-xl bg-white rounded-2xl sm:rounded-3xl shadow-xl overflow-hidden border border-slate-200/80 flex flex-col min-h-[600px]">
        <div className="bg-[#0b1b2d] px-5 pt-6 pb-5 text-white border-b border-white/10 shrink-0">
          <h2 className="text-xl font-extrabold tracking-tight text-white">Randevularım</h2>
          <p className="text-xs text-blue-200/70 mt-0.5">Planlanan ve geçmiş randevularınız</p>

          <div className="relative mt-4">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="İşletme veya hizmet ara..."
              className="w-full bg-white text-slate-900 placeholder:text-slate-400 text-xs sm:text-sm pl-10 pr-9 py-2.5 rounded-xl sm:rounded-2xl border border-white/20 focus:outline-none focus:ring-2 focus:ring-sky-400 shadow-sm"
            />
            {searchQuery && (
              <button
                onClick={() => setSearchQuery("")}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 p-0.5"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        </div>

        <div className="bg-white border-b border-slate-200 px-5 flex items-center gap-6 sm:gap-8 text-xs sm:text-sm font-semibold shrink-0">
          {([
            ["upcoming", "Yaklaşanlar"],
            ["past", "Geçmiş"],
            ["cancelled", "İptal Edilenler"],
          ] as [TabType, string][]).map(([tab, label]) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`py-3.5 transition relative flex items-center gap-1.5 cursor-pointer ${
                activeTab === tab ? "text-[#0d2137] font-bold" : "text-slate-500 hover:text-slate-800"
              }`}
            >
              <span>{label}</span>
              {counts[tab] > 0 && (
                <span
                  className={`px-1.5 py-0.5 rounded-full text-[10px] font-bold ${
                    activeTab === tab
                      ? tab === "cancelled"
                        ? "bg-rose-700 text-white"
                        : "bg-[#0d2137] text-white"
                      : "bg-slate-100 text-slate-600"
                  }`}
                >
                  {counts[tab]}
                </span>
              )}
              {activeTab === tab && (
                <span
                  className={`absolute bottom-0 left-0 right-0 h-0.5 rounded-t-full ${
                    tab === "cancelled" ? "bg-rose-600" : "bg-brand"
                  }`}
                />
              )}
            </button>
          ))}
        </div>

        <div className="p-4 sm:p-5 space-y-4 flex-1">
          {filtered.length === 0 ? (
            <div className="py-14 text-center px-4">
              <div className="w-14 h-14 rounded-2xl bg-blue-50 text-brand flex items-center justify-center mx-auto mb-3.5 border border-blue-100">
                <Calendar className="w-7 h-7" />
              </div>
              <h4 className="text-base font-bold text-slate-900">
                {activeTab === "upcoming" && "Yaklaşan aktif randevunuz yok"}
                {activeTab === "past" && "Geçmiş randevu kaydı bulunamadı"}
                {activeTab === "cancelled" && "İptal edilen randevunuz bulunmuyor"}
              </h4>
              <p className="text-xs text-slate-500 mt-1 max-w-xs mx-auto">
                {searchQuery
                  ? "Arama kriterinize uygun randevu bulunamadı."
                  : "Ana sayfadaki kuaför ve güzellik merkezlerinden dilediğiniz saati seçerek randevu oluşturabilirsiniz."}
              </p>
            </div>
          ) : (
            filtered.map((apt) => {
              const { dayName, dayNum, monthName } = calendarParts(apt.appointmentDate);
              const config = STATUS_CONFIG[apt.status];
              return (
                <div
                  key={apt.id}
                  onClick={() => setSelectedId(apt.id)}
                  className="bg-white rounded-2xl sm:rounded-3xl border border-slate-200/90 shadow-xs hover:shadow-md hover:border-slate-300 transition-all p-4 sm:p-5 cursor-pointer flex flex-col gap-4 group"
                >
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="w-11 h-11 rounded-full bg-[#0d2137] text-white flex items-center justify-center font-bold text-xs shrink-0">
                        {apt.business.name.substring(0, 2).toUpperCase()}
                      </div>
                      <div className="min-w-0">
                        <h3 className="font-extrabold text-sm sm:text-base text-slate-900 group-hover:text-brand transition truncate">
                          {apt.business.suspended ? apt.business.name : apt.business.name}
                        </h3>
                        <span className="text-[11px] text-slate-500">{apt.serviceItem.name}</span>
                      </div>
                    </div>
                    <ChevronRight className="w-4 h-4 text-slate-400 group-hover:text-slate-700 transition shrink-0" />
                  </div>

                  <div className="flex items-center justify-between pt-1 border-t border-slate-100">
                    <div className="text-xs sm:text-sm font-semibold text-slate-700 flex items-center gap-1.5">
                      <Clock className="w-3.5 h-3.5 text-brand" />
                      <span>{timeRange(apt)}</span>
                    </div>
                    <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold border ${config.classes}`}>
                      {config.icon}
                      {config.label}
                    </span>
                  </div>

                  <div className="flex items-center gap-4 bg-slate-50/70 p-3 rounded-2xl border border-slate-100">
                    <div className="flex flex-col shrink-0 w-14 rounded-xl overflow-hidden shadow-xs border border-slate-200 bg-white">
                      <div className="bg-[#0c1f36] text-white text-[11px] font-bold py-0.5 text-center uppercase">
                        {dayName}
                      </div>
                      <div className="py-1 text-center">
                        <div className="text-xl font-black text-slate-900 leading-none">{dayNum}</div>
                        <div className="text-[10px] font-bold text-slate-500 uppercase mt-0.5">{monthName}</div>
                      </div>
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-bold text-slate-800">₺{apt.serviceItem.price}</div>
                      <div className="flex items-center gap-3 text-xs text-slate-500 mt-1">
                        <span className="flex items-center gap-1">
                          <Clock className="w-3 h-3 text-slate-400" />
                          {apt.serviceItem.durationInMinutes} dk
                        </span>
                        <span className="flex items-center gap-1">
                          <MapPin className="w-3 h-3 text-slate-400" />
                          {apt.business.suspended ? "Askıya alındı" : apt.business.name}
                        </span>
                      </div>
                    </div>
                  </div>

                  {CANCELLABLE_STATUSES.includes(apt.status) && (
                    <div className="grid grid-cols-2 gap-2.5 pt-1">
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          openInMaps(apt.business.name);
                        }}
                        className="w-full flex items-center justify-center gap-1.5 py-2.5 px-3 rounded-xl bg-white hover:bg-slate-50 text-slate-700 font-semibold text-xs border border-slate-200 transition cursor-pointer"
                      >
                        <Navigation className="w-3.5 h-3.5 text-brand" />
                        <span>Yol Tarifi (Maps)</span>
                      </button>
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          addToGoogleCalendar(apt, apt.business.name);
                        }}
                        className="w-full flex items-center justify-center gap-1.5 py-2.5 px-3 rounded-xl bg-white hover:bg-slate-50 text-slate-700 font-semibold text-xs border border-slate-200 transition cursor-pointer"
                      >
                        <Calendar className="w-3.5 h-3.5 text-brand" />
                        <span>Takvime Ekle</span>
                      </button>
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
