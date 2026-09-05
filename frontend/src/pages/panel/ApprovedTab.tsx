import { useState, useEffect } from "react";
import api from "../../api/axios";
import { getErrorMessage } from "../../api/errors";
import Toast from "../../components/Toast";
import type { AppointmentResponse } from "../../types/api";

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

interface AppointmentCardProps {
  apt: AppointmentResponse;
  onNoShow: (appointmentId: number) => void;
  actionLoading: number | null;
  suspended: boolean;
}

function AppointmentCard({ apt, onNoShow, actionLoading, suspended }: AppointmentCardProps) {
  const [confirming, setConfirming] = useState(false);
  const isPast = new Date(apt.appointmentDate) < new Date();

  return (
    <div className="group bg-navy-50 border border-navy-100 rounded-2xl overflow-hidden hover:border-emerald-300 transition-all duration-300">
      <div className="bg-emerald-50 px-5 py-2.5 border-b border-emerald-100 flex items-center justify-between">
        <span className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-700">
          <span className="w-2 h-2 bg-emerald-500 rounded-full" />
          Onaylandı{isPast ? " — saati geçti" : ""}
        </span>
        <span className="text-xs text-slate-400">#{apt.id}</span>
      </div>

      <div className="p-5">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-5">
          <div className="space-y-1.5">
            <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Müşteri</p>
            <p className="text-slate-900 font-semibold text-sm">
              {apt.customer?.name} {apt.customer?.surName}
            </p>
            <p className="text-slate-500 text-xs flex items-center gap-1.5">
              📞 {apt.customer?.phone}
            </p>
          </div>

          <div className="space-y-1.5">
            <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Hizmet</p>
            <p className="text-slate-900 font-semibold text-sm">{apt.serviceItem?.name}</p>
            <div className="flex items-center gap-3 text-xs text-slate-500">
              <span>💰 {apt.serviceItem?.price} ₺</span>
              <span>⏱ {apt.serviceItem?.durationInMinutes} dk</span>
            </div>
          </div>

          <div className="space-y-1.5">
            <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Tarih & Saat</p>
            <p className="text-slate-900 font-semibold text-sm">📅 {formatDate(apt.appointmentDate)}</p>
          </div>
        </div>

        <div className="pt-3 border-t border-navy-100">
          {suspended ? null : !confirming ? (
            <button
              onClick={() => setConfirming(true)}
              className="px-4 py-2 text-sm font-semibold text-orange-700 hover:text-white bg-orange-50 hover:bg-orange-600 border border-orange-200 hover:border-orange-600 rounded-xl transition-all duration-300 cursor-pointer"
            >
              🚷 Müşteri Gelmedi
            </button>
          ) : (
            <div className="flex items-center gap-3">
              <span className="text-sm text-slate-600">Emin misiniz?</span>
              <button
                onClick={() => onNoShow(apt.id)}
                disabled={actionLoading === apt.id}
                className="px-4 py-2 text-sm font-semibold text-white bg-orange-600 hover:bg-orange-500 rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-300 cursor-pointer"
              >
                {actionLoading === apt.id ? "İşleniyor..." : "Evet, gelmedi"}
              </button>
              <button
                onClick={() => setConfirming(false)}
                disabled={actionLoading === apt.id}
                className="px-4 py-2 text-sm font-medium text-slate-500 hover:text-slate-900 border border-navy-200 rounded-xl disabled:opacity-50 transition-all cursor-pointer"
              >
                Vazgeç
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

interface ToastState {
  message: string;
  type: "success" | "error";
}

// Onaylanmış randevular — işletme sahibinin "müşteri gelmedi" işaretlemesi
// için. AppointmentService.changeStatus'ta NO_SHOW sadece APPROVED
// durumundan gecerli (bkz. Faz 2.1), bu yuzden burada sadece APPROVED
// randevular gosteriliyor. /business/{id}/upcoming SADECE gelecekteki
// randevulari donuyor (appointmentDateAfter=now) -- ama musteri gelmedi
// isaretlemesi tam olarak randevu SAATI GECTIKTEN SONRA yapilir, o yuzden
// bilerek tum randevulari donen /business/{id} kullanilip client'ta
// APPROVED'a filtreleniyor.
interface ApprovedTabProps {
  businessId: number | null;
  suspended?: boolean;
  onCountChange?: (count: number) => void;
}

export default function ApprovedTab({ businessId, suspended = false, onCountChange }: ApprovedTabProps) {
  const [appointments, setAppointments] = useState<AppointmentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<ToastState | null>(null);
  const [actionLoading, setActionLoading] = useState<number | null>(null);

  useEffect(() => {
    onCountChange?.(appointments.length);
  }, [appointments, onCountChange]);

  useEffect(() => {
    if (businessId) fetchApproved();
  }, [businessId]);

  async function fetchApproved() {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get<AppointmentResponse[]>(`/api/appointments/business/${businessId}`);
      const approved = res.data
        .filter((a) => a.status === "APPROVED")
        .sort((a, b) => new Date(a.appointmentDate).getTime() - new Date(b.appointmentDate).getTime());
      setAppointments(approved);
    } catch {
      setError("Onaylanmış randevular yüklenirken hata oluştu.");
    } finally {
      setLoading(false);
    }
  }

  async function handleNoShow(appointmentId: number) {
    setActionLoading(appointmentId);
    try {
      await api.put(`/api/appointments/${appointmentId}/no_show`);
      setAppointments((prev) => prev.filter((a) => a.id !== appointmentId));
      setToast({ message: "🚷 Randevu 'gelmedi' olarak işaretlendi.", type: "success" });
    } catch (err) {
      setToast({ message: getErrorMessage(err, "İşlem sırasında bir hata oluştu."), type: "error" });
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
          <p className="text-red-500 text-lg mb-4">{error}</p>
          <button
            onClick={fetchApproved}
            className="text-brand hover:text-brand-hover text-sm font-medium cursor-pointer"
          >
            Tekrar Dene
          </button>
        </div>
      )}

      {!loading && !error && appointments.length === 0 && (
        <div className="text-center py-16">
          <div className="w-20 h-20 bg-slate-100 rounded-full flex items-center justify-center mx-auto mb-5">
            <span className="text-4xl">📭</span>
          </div>
          <p className="text-slate-900 text-lg font-medium mb-1">Onaylanmış randevu yok</p>
          <p className="text-slate-500 text-sm">İstek Kutusu'ndan onayladığınız randevular burada görünür.</p>
        </div>
      )}

      {!loading && !error && appointments.length > 0 && (
        <div className="space-y-4">
          {appointments.map((apt) => (
            <AppointmentCard key={apt.id} apt={apt} onNoShow={handleNoShow} actionLoading={actionLoading} suspended={suspended} />
          ))}
        </div>
      )}
    </div>
  );
}
