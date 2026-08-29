import { useState, useEffect } from "react";
import api from "../../api/axios";
import { getErrorMessage } from "../../api/errors";
import Toast from "../../components/Toast";
import type { AppointmentResponse } from "../../types/api";

// Arka plan yenileme araligi. Backend'deki zaman asimi job'i varsayilan
// olarak 5 dakikada bir calisiyor; buradaki aralik ondan belirgin sekilde
// kisa olmali ki ekrandaki liste sunucudan uzun sure geride kalmasin.
const REFRESH_INTERVAL_MS = 60_000;

function formatDate(dateStr: string) {
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

interface ToastState {
  message: string;
  type: "success" | "error";
}

// Bekleyen randevu talepleri — eskiden PendingAppointments.jsx sayfasıydı,
// businessId sabit kodluydu (=1). Artık BusinessPanelPage'den seçili
// işletmenin id'sini prop olarak alıyor.
export default function InboxTab({ businessId }: { businessId: number | null }) {
  const [appointments, setAppointments] = useState<AppointmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<ToastState | null>(null);
  const [actionLoading, setActionLoading] = useState<number | null>(null);

  useEffect(() => {
    if (!businessId) return;
    fetchPending();

    // Talepler arka planda zaman aşımına uğrayabiliyor (bkz. backend
    // AppointmentExpiryPolicy) ve bu sayfa açık bırakılan bir sekmede
    // dakikalarca durabiliyor. Yenileme olmadan işletme, sunucuda çoktan
    // düşmüş bir talebi ekranda görüp onaylamaya çalışıyor ve anlamsız bir
    // hata alıyordu -- bu "kötü şans" değil, panelin normal kullanımı.
    const interval = setInterval(() => fetchPending({ silent: true }), REFRESH_INTERVAL_MS);

    // Sekmeye geri dönüldüğünde beklemeden tazele: kullanıcı başka
    // sekmedeyken periyodik yenileme çalışmış olsa bile, döndüğü anda en
    // güncel listeyi görmesi gerekiyor.
    const onFocus = () => fetchPending({ silent: true });
    window.addEventListener("focus", onFocus);

    return () => {
      clearInterval(interval);
      window.removeEventListener("focus", onFocus);
    };
  }, [businessId]);

  // silent: arka plan yenilemelerinde yükleniyor göstergesini yakmıyoruz --
  // aksi halde liste her dakika gözünün önünde "yükleniyor"a dönerdi.
  async function fetchPending({ silent = false } = {}) {
    if (!silent) setLoading(true);
    setError(null);
    try {
      const res = await api.get<AppointmentResponse[]>(`/api/appointments/business/${businessId}/pending`);
      setAppointments(res.data);
    } catch {
      if (!silent) setError("Bekleyen randevular yüklenirken hata oluştu.");
    } finally {
      if (!silent) setLoading(false);
    }
  }

  async function handleAction(appointmentId: number, action: "approve" | "reject") {
    setActionLoading(appointmentId);
    try {
      await api.put(`/api/appointments/${appointmentId}/${action}`);
      setAppointments((prev) => prev.filter((a) => a.id !== appointmentId));
      setToast({
        message: action === "approve" ? "✅ Randevu onaylandı!" : "❌ Randevu reddedildi.",
        type: action === "approve" ? "success" : "error",
      });
    } catch (err) {
      setToast({ message: getErrorMessage(err, "İşlem sırasında bir hata oluştu."), type: "error" });
      // Hata mesajı tek başına yetmiyor: işlem başarısız olduysa ekrandaki
      // liste sunucudaki gerçekle uyuşmuyor demektir (en tipik hali: talep
      // bu arada zaman aşımına uğramış). Tazelemezsek kullanıcı aynı satıra
      // tekrar tekrar basıp aynı hatayı almaya devam eder.
      fetchPending({ silent: true });
    } finally {
      setActionLoading(null);
    }
  }

  return (
    <div>
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      {loading && (
        <div className="flex justify-center py-16">
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
          <button
            onClick={() => fetchPending()}
            className="text-emerald-400 hover:text-emerald-300 text-sm font-medium cursor-pointer"
          >
            Tekrar Dene
          </button>
        </div>
      )}

      {!loading && !error && appointments.length === 0 && (
        <div className="text-center py-16">
          <div className="w-20 h-20 bg-emerald-500/10 rounded-full flex items-center justify-center mx-auto mb-5">
            <span className="text-4xl">🎉</span>
          </div>
          <p className="text-white text-lg font-medium mb-1">Tüm istekler işlendi!</p>
          <p className="text-slate-400 text-sm">Bekleyen randevu talebi bulunmuyor.</p>
        </div>
      )}

      {!loading && !error && appointments.length > 0 && (
        <div className="space-y-4">
          <div className="flex items-center gap-2 mb-2">
            <span className="inline-flex items-center gap-1.5 px-3 py-1 bg-amber-500/10 border border-amber-500/20 rounded-full text-xs font-medium text-amber-400">
              <span className="w-1.5 h-1.5 bg-amber-400 rounded-full animate-pulse" />
              {appointments.length} bekleyen istek
            </span>
          </div>

          {appointments.map((apt) => (
            <div
              key={apt.id}
              className="group bg-surface/80 backdrop-blur-sm border border-white/10 rounded-2xl overflow-hidden hover:border-amber-500/20 transition-all duration-300"
            >
              <div className="bg-gradient-to-r from-amber-500/10 to-orange-500/10 px-5 py-2.5 border-b border-white/5 flex items-center justify-between">
                <span className="inline-flex items-center gap-1.5 text-xs font-medium text-amber-400">
                  <span className="w-2 h-2 bg-amber-400 rounded-full animate-pulse" />
                  Onay Bekliyor
                </span>
                <span className="text-xs text-slate-500">#{apt.id}</span>
              </div>

              <div className="p-5">
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-5">
                  <div className="space-y-1.5">
                    <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Müşteri</p>
                    <p className="text-white font-semibold text-sm">
                      {apt.customer?.name} {apt.customer?.surName}
                    </p>
                    <p className="text-slate-400 text-xs flex items-center gap-1.5">
                      📞 {apt.customer?.phone}
                    </p>
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
                    <p className="text-white font-semibold text-sm">
                      📅 {formatDate(apt.appointmentDate)}
                    </p>
                    {/* Bu uctan donen talepler zaten hep PENDING, yani expiresAt her zaman dolu
                        geliyor (bkz. backend AppointmentService.expiresAt) -- formulu burada
                        tekrar uretmiyoruz, sunucudan geldigi gibi gosteriyoruz. */}
                    {apt.expiresAt && (
                      <p className="text-[11px] text-amber-400/80">
                        ⏰ Son yanıt: {formatDate(apt.expiresAt)}
                      </p>
                    )}
                  </div>
                </div>

                <div className="flex gap-3 pt-3 border-t border-white/5">
                  <button
                    onClick={() => handleAction(apt.id, "approve")}
                    disabled={actionLoading === apt.id}
                    className="flex-1 py-2.5 text-sm font-semibold text-white bg-gradient-to-r from-emerald-600 to-green-600 hover:from-emerald-500 hover:to-green-500 rounded-xl shadow-lg shadow-emerald-500/20 hover:shadow-emerald-500/30 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-300 transform hover:scale-[1.02] active:scale-[0.98] cursor-pointer"
                  >
                    {actionLoading === apt.id ? "İşleniyor..." : "✅ Onayla"}
                  </button>
                  <button
                    onClick={() => handleAction(apt.id, "reject")}
                    disabled={actionLoading === apt.id}
                    className="flex-1 py-2.5 text-sm font-semibold text-white bg-gradient-to-r from-rose-700 to-red-700 hover:from-rose-600 hover:to-red-600 rounded-xl shadow-lg shadow-rose-500/20 hover:shadow-rose-500/30 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-300 transform hover:scale-[1.02] active:scale-[0.98] cursor-pointer"
                  >
                    {actionLoading === apt.id ? "İşleniyor..." : "❌ Reddet"}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
