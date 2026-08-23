import { useState, useEffect } from "react";
import api from "../api/axios";
import Toast from "../components/Toast";
import StarRating from "../components/StarRating";

function formatDate(dateStr) {
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

const STATUS_CONFIG = {
  PENDING: { label: "Onay Bekliyor", icon: "⏳", classes: "bg-amber-500/10 border-amber-500/20 text-amber-400" },
  APPROVED: { label: "Onaylandı", icon: "✅", classes: "bg-emerald-500/10 border-emerald-500/20 text-emerald-400" },
  REJECTED: { label: "Reddedildi", icon: "❌", classes: "bg-red-500/10 border-red-500/20 text-red-400" },
  CANCELLED: { label: "İptal Edildi", icon: "🚫", classes: "bg-slate-500/10 border-slate-500/20 text-slate-400" },
  COMPLETED: { label: "Tamamlandı", icon: "🎉", classes: "bg-teal-500/10 border-teal-500/20 text-teal-400" },
  NO_SHOW: { label: "Gelinmedi", icon: "🚷", classes: "bg-orange-500/10 border-orange-500/20 text-orange-400" },
};

// Randevu iptali sadece PENDING/APPROVED durumundaki randevular için
// AppointmentService.changeStatus'ta izin veriliyor (bkz. backend) — buton
// bu iki durum dışında hiç gösterilmiyor, aksi halde tıklanınca 409 dönerdi.
const CANCELLABLE_STATUSES = ["PENDING", "APPROVED"];

function StatusBadge({ status }) {
  const config = STATUS_CONFIG[status] ?? { label: status, icon: "", classes: "bg-slate-500/10 border-slate-500/20 text-slate-400" };
  return (
    <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full border text-xs font-medium ${config.classes}`}>
      {config.icon} {config.label}
    </span>
  );
}

// Sadece COMPLETED randevularda gösterilir -- backend zaten bunu ZORUNLU
// kılıyor (ReviewService.createReview, status != COMPLETED ise 409),
// bu sadece UX: yanlış durumdaki bir randevuda tıklanıp hata almasın.
function ReviewForm({ appointmentId, onSubmitted, onCancel }) {
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  async function handleSubmit(e) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await api.post("/api/reviews/create", { appointmentId, rating, comment: comment || null });
      onSubmitted();
    } catch (err) {
      setError(String(err.response?.data?.message || err.response?.data || "Yorum gönderilirken hata oluştu."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3">
      <div className="flex items-center gap-3">
        <span className="text-sm text-slate-300">Puanınız:</span>
        <StarRating value={rating} onChange={setRating} interactive size="text-2xl" />
      </div>
      <textarea
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        maxLength={1000}
        rows={3}
        placeholder="Deneyiminizi paylaşın (opsiyonel)"
        className="w-full px-3 py-2 bg-bg-light border border-white/10 rounded-lg text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500/50 transition-all resize-none"
      />
      {error && <p className="text-xs text-red-400">{error}</p>}
      <div className="flex gap-3">
        <button
          type="submit"
          disabled={saving}
          className="px-4 py-2 text-sm font-semibold text-white bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-400 hover:to-orange-400 rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-all cursor-pointer"
        >
          {saving ? "Gönderiliyor..." : "Yorumu Gönder"}
        </button>
        <button
          type="button"
          onClick={onCancel}
          disabled={saving}
          className="px-4 py-2 text-sm font-medium text-slate-300 hover:text-white border border-white/10 rounded-xl disabled:opacity-50 transition-all cursor-pointer"
        >
          Vazgeç
        </button>
      </div>
    </form>
  );
}

function AppointmentCard({ apt, onCancel, cancelLoading, onReviewSubmitted }) {
  const [confirming, setConfirming] = useState(false);
  const [reviewing, setReviewing] = useState(false);

  return (
    <div className="bg-surface/80 backdrop-blur-sm border border-white/10 rounded-2xl overflow-hidden">
      <div className="px-5 py-2.5 border-b border-white/5 flex items-center justify-between">
        <StatusBadge status={apt.status} />
        <span className="text-xs text-slate-500">#{apt.id}</span>
      </div>

      <div className="p-5">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-4">
          <div className="space-y-1.5">
            <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">İşletme</p>
            <p className="text-white font-semibold text-sm">{apt.business?.name}</p>
          </div>

          <div className="space-y-1.5">
            <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Hizmet</p>
            <p className="text-white font-semibold text-sm">{apt.serviceItem?.name}</p>
            <div className="flex items-center gap-3 text-xs text-slate-400">
              <span>💰 {apt.serviceItem?.price} ₺</span>
              <span>⏱ {apt.serviceItem?.durationInMinutes} dk</span>
            </div>
          </div>

          <div className="space-y-1.5">
            <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Tarih & Saat</p>
            <p className="text-white font-semibold text-sm">📅 {formatDate(apt.appointmentDate)}</p>
          </div>
        </div>

        {CANCELLABLE_STATUSES.includes(apt.status) && (
          <div className="pt-3 border-t border-white/5">
            {!confirming ? (
              <button
                onClick={() => setConfirming(true)}
                className="w-full sm:w-auto px-4 py-2 text-sm font-semibold text-red-400 hover:text-white bg-red-500/10 hover:bg-red-600 border border-red-500/20 hover:border-red-600 rounded-xl transition-all duration-300 cursor-pointer"
              >
                Randevuyu İptal Et
              </button>
            ) : (
              <div className="flex items-center gap-3">
                <span className="text-sm text-slate-300">Emin misiniz?</span>
                <button
                  onClick={() => onCancel(apt.id)}
                  disabled={cancelLoading === apt.id}
                  className="px-4 py-2 text-sm font-semibold text-white bg-red-600 hover:bg-red-500 rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-300 cursor-pointer"
                >
                  {cancelLoading === apt.id ? "İptal ediliyor..." : "Evet, iptal et"}
                </button>
                <button
                  onClick={() => setConfirming(false)}
                  disabled={cancelLoading === apt.id}
                  className="px-4 py-2 text-sm font-medium text-slate-300 hover:text-white border border-white/10 rounded-xl disabled:opacity-50 transition-all cursor-pointer"
                >
                  Vazgeç
                </button>
              </div>
            )}
          </div>
        )}

        {apt.status === "COMPLETED" && !apt.hasReview && (
          <div className="pt-3 border-t border-white/5">
            {!reviewing ? (
              <button
                onClick={() => setReviewing(true)}
                className="w-full sm:w-auto px-4 py-2 text-sm font-semibold text-amber-400 hover:text-white bg-amber-500/10 hover:bg-amber-600 border border-amber-500/20 hover:border-amber-600 rounded-xl transition-all duration-300 cursor-pointer"
              >
                ⭐ Yorum Yap
              </button>
            ) : (
              <ReviewForm
                appointmentId={apt.id}
                onCancel={() => setReviewing(false)}
                onSubmitted={() => {
                  setReviewing(false);
                  onReviewSubmitted(apt.id);
                }}
              />
            )}
          </div>
        )}
      </div>
    </div>
  );
}

// Müşterinin kendi randevu geçmişi — /appointments/me tüm durumdaki
// randevuları döner (backend kimliği token'dan alıyor, path'te id yok,
// bu yüzden başka bir kullanıcının randevusu asla görünmez). Yaklaşan/
// geçmiş ayrımı burada, istemci tarafında tarihe göre yapılıyor.
export default function MyAppointmentsPage() {
  const [appointments, setAppointments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [toast, setToast] = useState(null);
  const [cancelLoading, setCancelLoading] = useState(null);

  useEffect(() => {
    fetchAppointments();
  }, []);

  async function fetchAppointments() {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get("/api/appointments/me");
      setAppointments(res.data);
    } catch (err) {
      setError("Randevularınız yüklenirken hata oluştu.");
    } finally {
      setLoading(false);
    }
  }

  function handleReviewSubmitted(appointmentId) {
    setAppointments((prev) =>
      prev.map((a) => (a.id === appointmentId ? { ...a, hasReview: true } : a))
    );
    setToast({ message: "⭐ Yorumunuz kaydedildi, teşekkürler!", type: "success" });
  }

  async function handleCancel(appointmentId) {
    setCancelLoading(appointmentId);
    try {
      const res = await api.put(`/api/appointments/${appointmentId}/cancel`);
      setAppointments((prev) => prev.map((a) => (a.id === appointmentId ? res.data : a)));
      setToast({ message: "🚫 Randevu iptal edildi.", type: "success" });
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data || "İptal sırasında bir hata oluştu.";
      setToast({ message: String(msg), type: "error" });
    } finally {
      setCancelLoading(null);
    }
  }

  const now = new Date();
  const upcoming = appointments
    .filter((a) => new Date(a.appointmentDate) >= now)
    .sort((a, b) => new Date(a.appointmentDate) - new Date(b.appointmentDate));
  const past = appointments
    .filter((a) => new Date(a.appointmentDate) < now)
    .sort((a, b) => new Date(b.appointmentDate) - new Date(a.appointmentDate));

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      <div className="mb-8">
        <h1 className="text-2xl sm:text-3xl font-bold text-white flex items-center gap-3">
          <span className="w-10 h-10 bg-gradient-to-br from-emerald-500 to-teal-600 rounded-xl flex items-center justify-center shadow-lg shadow-emerald-500/20">
            📅
          </span>
          Randevularım
        </h1>
      </div>

      {loading && (
        <div className="flex justify-center py-20">
          <div className="flex items-center gap-3 text-slate-400">
            <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
            Yükleniyor...
          </div>
        </div>
      )}

      {error && !loading && (
        <div className="text-center py-16">
          <p className="text-5xl mb-4">⚠️</p>
          <p className="text-red-400 text-lg mb-4">{error}</p>
          <button onClick={fetchAppointments} className="text-emerald-400 hover:text-emerald-300 text-sm font-medium cursor-pointer">
            Tekrar Dene
          </button>
        </div>
      )}

      {!loading && !error && appointments.length === 0 && (
        <div className="text-center py-20">
          <div className="w-20 h-20 bg-slate-500/10 rounded-full flex items-center justify-center mx-auto mb-5">
            <span className="text-4xl">📭</span>
          </div>
          <p className="text-white text-lg font-medium mb-1">Henüz randevunuz yok</p>
          <p className="text-slate-400 text-sm">Bir işletme seçip randevu talebi oluşturabilirsiniz.</p>
        </div>
      )}

      {!loading && !error && appointments.length > 0 && (
        <div className="space-y-10">
          <section>
            <h2 className="text-sm font-semibold text-white mb-3 flex items-center gap-2">
              Yaklaşan Randevular
              <span className="text-xs font-normal text-slate-500">({upcoming.length})</span>
            </h2>
            {upcoming.length === 0 ? (
              <p className="text-slate-400 text-sm">Yaklaşan randevunuz yok.</p>
            ) : (
              <div className="space-y-4">
                {upcoming.map((apt) => (
                  <AppointmentCard
                    key={apt.id}
                    apt={apt}
                    onCancel={handleCancel}
                    cancelLoading={cancelLoading}
                    onReviewSubmitted={handleReviewSubmitted}
                  />
                ))}
              </div>
            )}
          </section>

          <section>
            <h2 className="text-sm font-semibold text-white mb-3 flex items-center gap-2">
              Geçmiş Randevular
              <span className="text-xs font-normal text-slate-500">({past.length})</span>
            </h2>
            {past.length === 0 ? (
              <p className="text-slate-400 text-sm">Geçmiş randevunuz yok.</p>
            ) : (
              <div className="space-y-4">
                {past.map((apt) => (
                  <AppointmentCard
                    key={apt.id}
                    apt={apt}
                    onCancel={handleCancel}
                    cancelLoading={cancelLoading}
                    onReviewSubmitted={handleReviewSubmitted}
                  />
                ))}
              </div>
            )}
          </section>
        </div>
      )}
    </div>
  );
}
