import { useState, useEffect, type ChangeEvent } from "react";
import { useParams, useNavigate } from "react-router-dom";
import api from "../api/axios";
import { getErrorMessage } from "../api/errors";
import Toast from "../components/Toast";
import LocationPicker from "../components/LocationPicker";
import StarRating from "../components/StarRating";
import { useAuth } from "../context/AuthContext";
import type { AppointmentRequest, BusinessDetailResponse, ReviewResponse, ServiceItemResponse } from "../types/api";

function formatReviewDate(dateStr: string) {
  const date = new Date(dateStr);
  const months = [
    "Oca", "Şub", "Mar", "Nis", "May", "Haz",
    "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara",
  ];
  return `${date.getDate()} ${months[date.getMonth()]} ${date.getFullYear()}`;
}

function formatTime(timeStr: string | null | undefined) {
  // "09:45:00" → "09:45"
  if (!timeStr) return "";
  const parts = timeStr.split(":");
  return `${parts[0]}:${parts[1]}`;
}

interface ToastState {
  message: string;
  type: "success" | "error";
}

// Manuel hizmet ID girisinde (services bos donerse) sadece bu dort alan
// uyduruluyor -- gercek ServiceItemResponse'un description'ini icermez,
// bu yuzden ServiceItemResponse'un TAMAMI degil, sadece bu bilesende
// gercekten kullanilan alt kumesi.
type SelectableService = Pick<ServiceItemResponse, "id" | "name" | "price" | "durationInMinutes">;

export default function BusinessDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const [business, setBusiness] = useState<BusinessDetailResponse | null>(null);
  const [isFavorited, setIsFavorited] = useState(false);
  const [services, setServices] = useState<ServiceItemResponse[]>([]);
  const [reviews, setReviews] = useState<ReviewResponse[]>([]);
  const [reviewsLoading, setReviewsLoading] = useState(true);
  const [selectedService, setSelectedService] = useState<SelectableService | null>(null);
  const [selectedDate, setSelectedDate] = useState("");
  const [slots, setSlots] = useState<string[]>([]);
  const [selectedSlot, setSelectedSlot] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [slotsLoading, setSlotsLoading] = useState(false);
  const [bookingLoading, setBookingLoading] = useState(false);
  const [toast, setToast] = useState<ToastState | null>(null);

  // İşletme bilgilerini çek
  useEffect(() => {
    async function fetchBusiness() {
      try {
        const res = await api.get<BusinessDetailResponse[]>("/api/businesses");
        const found = res.data.find((b) => b.id === Number(id));
        if (found) {
          setBusiness(found);
          // serviceItems business nesnesinin içinde gelebilir
          if (found.serviceItems && found.serviceItems.length > 0) {
            setServices(found.serviceItems);
          }
        } else {
          setToast({ message: "İşletme bulunamadı.", type: "error" });
        }
      } catch {
        setToast({ message: "İşletme bilgileri yüklenemedi.", type: "error" });
      } finally {
        setLoading(false);
      }
    }
    fetchBusiness();
  }, [id]);

  // Yorumları çek
  useEffect(() => {
    async function fetchReviews() {
      setReviewsLoading(true);
      try {
        const res = await api.get<ReviewResponse[]>(`/api/reviews/business/${id}`);
        setReviews(res.data);
      } catch {
        // Sessizce yut -- yorumlar sayfanin ana islevi (randevu alma) icin
        // kritik degil, ayri bir hata toast'i gereksiz gurultu olurdu.
      } finally {
        setReviewsLoading(false);
      }
    }
    fetchReviews();
  }, [id]);

  // Favori durumu -- ayrı bir "var mı" ucu yok, /me listesinde arıyoruz.
  // Tek bir detay sayfası yüklemesi için kabul edilebilir, HomePage'deki
  // gibi toplu bir liste değil.
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

  // Boş saatleri getir
  async function fetchSlots() {
    if (!selectedDate || !selectedService) {
      setToast({ message: "Lütfen önce bir hizmet ve tarih seçin.", type: "error" });
      return;
    }

    setSlotsLoading(true);
    setSlots([]);
    setSelectedSlot(null);

    try {
      const res = await api.get<string[]>("/api/appointments/available-slots", {
        params: {
          businessId: id,
          serviceId: selectedService.id,
          date: selectedDate,
        },
      });
      setSlots(res.data);
      if (res.data.length === 0) {
        setToast({ message: "Bu tarihte uygun saat bulunamadı.", type: "error" });
      }
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Saatler alınırken hata oluştu."), type: "error" });
    } finally {
      setSlotsLoading(false);
    }
  }

  // Randevu oluştur
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
      setSelectedSlot(null);

      // Saatleri yenile
      const res = await api.get<string[]>("/api/appointments/available-slots", {
        params: {
          businessId: id,
          serviceId: selectedService.id,
          date: selectedDate,
        },
      });
      setSlots(res.data);
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Randevu oluşturulurken hata oluştu."), type: "error" });
    } finally {
      setBookingLoading(false);
    }
  }

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

  if (!business) {
    return (
      <div className="text-center py-20">
        <p className="text-5xl mb-4">😕</p>
        <p className="text-slate-400 text-lg">İşletme bulunamadı.</p>
        <button onClick={() => navigate("/")} className="mt-4 text-emerald-400 hover:text-emerald-300 text-sm cursor-pointer">
          ← Ana Sayfaya Dön
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      {/* Back Button */}
      <button
        onClick={() => navigate("/")}
        className="flex items-center gap-2 text-sm text-slate-400 hover:text-white mb-6 transition-colors cursor-pointer"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
        </svg>
        İşletmelere Dön
      </button>

      {/* Business Info Card */}
      <div className="bg-surface/80 backdrop-blur-xl border border-white/10 rounded-2xl overflow-hidden mb-6">
        <div className="bg-gradient-to-r from-emerald-600 to-teal-600 px-6 py-5">
          <div className="flex items-start justify-between gap-3">
            <h1 className="text-2xl font-bold text-white">{business.name}</h1>
            {isAuthenticated && (
              <button
                onClick={toggleFavorite}
                title={isFavorited ? "Favorilerden çıkar" : "Favorilere ekle"}
                className="shrink-0 w-9 h-9 rounded-full flex items-center justify-center text-base bg-black/15 hover:bg-black/25 transition-colors cursor-pointer"
              >
                {isFavorited ? "❤️" : "🤍"}
              </button>
            )}
          </div>
          {/* averageRating null kontrolu BusinessCard'daki (PR3) ayni gerekce --
              backend sozlesmesi reviewCount>0 iken averageRating'in dolu
              olacagini garanti ediyor ama TS bunu tek basina cikaramiyor. */}
          {business.reviewCount > 0 && business.averageRating != null ? (
            <div className="flex items-center gap-1.5 mt-1.5">
              <StarRating value={business.averageRating} size="text-sm" />
              <span className="text-emerald-100 text-sm">
                {business.averageRating.toFixed(1)} ({business.reviewCount} değerlendirme)
              </span>
            </div>
          ) : (
            <p className="text-emerald-100/70 text-sm mt-1.5 italic">Henüz değerlendirme yok</p>
          )}
          <div className="flex flex-wrap gap-4 mt-2 text-emerald-100 text-sm">
            {business.address && (
              <span className="flex items-center gap-1.5">📍 {business.address}</span>
            )}
            {business.phone && (
              <span className="flex items-center gap-1.5">📞 {business.phone}</span>
            )}
            {(business.openTime || business.closeTime) && (
              <span className="flex items-center gap-1.5">
                🕐 {business.openTime?.slice(0, 5)} — {business.closeTime?.slice(0, 5)}
              </span>
            )}
          </div>
        </div>

        {business.description && (
          <div className="px-6 py-4 border-b border-white/5">
            <p className="text-sm text-slate-400">{business.description}</p>
          </div>
        )}

        {/* Konum — işletme sahibi henüz konum girmemişse (Faz 2.8) hiç
            gösterilmiyor, boş bir harita göstermenin anlamı yok. */}
        {business.latitude != null && business.longitude != null && (
          <div className="px-6 py-4">
            <LocationPicker latitude={business.latitude} longitude={business.longitude} readOnly height={180} />
          </div>
        )}
      </div>

      {/* Appointment Booking Card */}
      <div className="bg-surface/80 backdrop-blur-xl border border-white/10 rounded-2xl overflow-hidden">
        <div className="px-6 py-4 border-b border-white/5">
          <h2 className="text-lg font-semibold text-white">📅 Randevu Al</h2>
        </div>

        <div className="p-6 space-y-6">
          {/* Step 1: Service Selection */}
          <div className="space-y-2">
            <label className="block text-sm font-medium text-slate-300">
              1. Hizmet Seçin
            </label>
            {services.length > 0 ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {services.map((svc) => (
                  <button
                    key={svc.id}
                    onClick={() => {
                      setSelectedService(svc);
                      setSlots([]);
                      setSelectedSlot(null);
                    }}
                    className={`p-4 text-left rounded-xl border transition-all duration-200 cursor-pointer ${
                      selectedService?.id === svc.id
                        ? "border-emerald-500 bg-emerald-500/10 text-emerald-400"
                        : "border-white/10 bg-bg-light text-slate-300 hover:border-white/20"
                    }`}
                  >
                    <p className="font-medium text-sm">{svc.name}</p>
                    <div className="flex items-center gap-3 mt-1.5 text-xs text-slate-500">
                      <span>⏱ {svc.durationInMinutes} dk</span>
                      <span>💰 {svc.price} ₺</span>
                    </div>
                  </button>
                ))}
              </div>
            ) : (
              <p className="text-sm text-slate-500 italic">
                Bu işletmeye ait hizmet bilgisi bulunamadı. Lütfen hizmet ID'sini el ile girin.
              </p>
            )}

            {/* Manual Service ID — Fallback when no serviceItems */}
            {services.length === 0 && (
              <div className="mt-2">
                <input
                  type="number"
                  min="1"
                  placeholder="Hizmet ID (örn: 1)"
                  onChange={(e: ChangeEvent<HTMLInputElement>) => {
                    const val = Number(e.target.value);
                    if (val > 0) {
                      setSelectedService({ id: val, name: `Hizmet #${val}`, durationInMinutes: 0, price: 0 });
                    }
                  }}
                  className="w-full px-4 py-2.5 bg-bg-light border border-white/10 rounded-xl text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 transition-all"
                />
              </div>
            )}
          </div>

          {/* Step 2: Date Selection */}
          <div className="space-y-2">
            <label htmlFor="date-picker" className="block text-sm font-medium text-slate-300">
              2. Tarih Seçin
            </label>
            <input
              id="date-picker"
              type="date"
              value={selectedDate}
              onChange={(e) => {
                setSelectedDate(e.target.value);
                setSlots([]);
                setSelectedSlot(null);
              }}
              className="w-full px-4 py-3 bg-bg-light border border-white/10 rounded-xl text-white text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500/50 transition-all hover:border-white/20"
            />
          </div>

          {/* Fetch Slots Button */}
          <button
            onClick={fetchSlots}
            disabled={slotsLoading || !selectedDate || !selectedService}
            className="w-full py-3 bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-semibold text-sm rounded-xl shadow-lg shadow-emerald-500/25 disabled:opacity-40 disabled:cursor-not-allowed transition-all duration-300 transform hover:scale-[1.02] active:scale-[0.98] cursor-pointer"
          >
            {slotsLoading ? (
              <span className="flex items-center justify-center gap-2">
                <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
                Yükleniyor...
              </span>
            ) : (
              "🔍 Boş Saatleri Getir"
            )}
          </button>

          {/* Step 3: Time Slots */}
          {slots.length > 0 && (
            <div className="space-y-3 animate-fade-in">
              <div className="flex items-center gap-2">
                <div className="h-px flex-1 bg-gradient-to-r from-transparent via-white/10 to-transparent" />
                <span className="text-xs font-medium text-slate-400 uppercase tracking-widest">
                  3. Saat Seçin
                </span>
                <div className="h-px flex-1 bg-gradient-to-r from-transparent via-white/10 to-transparent" />
              </div>

              <div className="grid grid-cols-3 sm:grid-cols-4 gap-2.5">
                {slots.map((slot) => (
                  <button
                    key={slot}
                    onClick={() => setSelectedSlot(slot)}
                    className={`py-2.5 px-2 rounded-xl text-sm font-medium transition-all duration-200 cursor-pointer ${
                      selectedSlot === slot
                        ? "bg-emerald-500 text-white shadow-lg shadow-emerald-500/30 scale-105"
                        : "bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/20 hover:border-emerald-400/50"
                    }`}
                  >
                    🕐 {formatTime(slot)}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Step 4: Confirm Booking */}
          {selectedSlot && (
            <div className="space-y-3 animate-fade-in">
              <div className="bg-emerald-500/10 border border-emerald-500/20 rounded-xl p-4">
                <p className="text-sm text-emerald-300 text-center">
                  <strong>{selectedDate}</strong> tarihinde{" "}
                  <strong>{formatTime(selectedSlot)}</strong> saati için randevu oluşturulacak.
                </p>
              </div>

              <button
                onClick={handleBooking}
                disabled={bookingLoading}
                className="w-full py-3.5 bg-gradient-to-r from-green-600 to-emerald-600 hover:from-green-500 hover:to-emerald-500 text-white font-bold text-sm rounded-xl shadow-lg shadow-green-500/25 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-300 transform hover:scale-[1.02] active:scale-[0.98] cursor-pointer"
              >
                {bookingLoading ? "Oluşturuluyor..." : "✅ Randevuyu Onayla"}
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Reviews Card */}
      <div className="bg-surface/80 backdrop-blur-xl border border-white/10 rounded-2xl overflow-hidden mt-6">
        <div className="px-6 py-4 border-b border-white/5">
          <h2 className="text-lg font-semibold text-white">⭐ Değerlendirmeler</h2>
        </div>

        <div className="p-6">
          {reviewsLoading ? (
            <div className="flex justify-center py-8">
              <div className="flex items-center gap-3 text-slate-400 text-sm">
                <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
                Yükleniyor...
              </div>
            </div>
          ) : reviews.length === 0 ? (
            <p className="text-sm text-slate-500 text-center py-4">
              Bu işletme için henüz değerlendirme yapılmamış.
            </p>
          ) : (
            <div className="space-y-4">
              {reviews.map((review) => (
                <div key={review.id} className="border-b border-white/5 last:border-0 pb-4 last:pb-0">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-sm font-medium text-white">
                      {review.reviewer.name} {review.reviewer.surName?.charAt(0)}.
                    </span>
                    <span className="text-xs text-slate-500">{formatReviewDate(review.createdAt)}</span>
                  </div>
                  <StarRating value={review.rating} size="text-xs" />
                  {review.comment && (
                    <p className="text-sm text-slate-400 mt-1.5">{review.comment}</p>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Custom animations */}
      <style>{`
        @keyframes fade-in {
          from { opacity: 0; transform: translateY(10px); }
          to { opacity: 1; transform: translateY(0); }
        }
        .animate-fade-in {
          animation: fade-in 0.4s cubic-bezier(0.16, 1, 0.3, 1);
        }
        input[type="date"]::-webkit-calendar-picker-indicator {
          filter: invert(1) brightness(0.8);
          cursor: pointer;
        }
      `}</style>
    </div>
  );
}
