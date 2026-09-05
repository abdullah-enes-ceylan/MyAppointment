import { useState, useEffect, type ChangeEvent, type FormEvent } from "react";
import api from "../../api/axios";
import { getErrorMessage } from "../../api/errors";
import Toast from "../../components/Toast";
import type { BusinessClosureRequest, BusinessClosureResponse, DayOfWeek, WorkingHourRequest, WorkingHourResponse } from "../../types/api";

const DAYS: { key: DayOfWeek; label: string }[] = [
  { key: "MONDAY", label: "Pazartesi" },
  { key: "TUESDAY", label: "Salı" },
  { key: "WEDNESDAY", label: "Çarşamba" },
  { key: "THURSDAY", label: "Perşembe" },
  { key: "FRIDAY", label: "Cuma" },
  { key: "SATURDAY", label: "Cumartesi" },
  { key: "SUNDAY", label: "Pazar" },
];

// Gunluk UI durumu -- WorkingHourResponse/Request'ten farkli: openTime/
// closeTime burada her zaman string (bos "" dahil, input[type=time] boyle
// calisiyor), "configured" ise sadece bu ekranin kendi bilgisi (backend
// hic bu gunu donmemisse hala yapilandirilmamis demek).
interface DayState {
  openTime: string;
  closeTime: string;
  closed: boolean;
  configured: boolean;
}

type DaysState = Record<DayOfWeek, DayState>;

function emptyDayState(): DaysState {
  const state = {} as DaysState;
  for (const d of DAYS) {
    state[d.key] = { openTime: "", closeTime: "", closed: false, configured: false };
  }
  return state;
}

interface ToastState {
  message: string;
  type: "success" | "error";
}

// HTML <input type="time"> "HH:mm" verir, backend'deki LocalTime alanı
// (Jackson ISO_LOCAL_TIME) saniyesiz "HH:mm"i de kabul ediyor — ayrıca
// dönüştürmeye gerek yok.
export default function WorkingHoursTab({ businessId, suspended = false }: { businessId: number | null; suspended?: boolean }) {
  const [days, setDays] = useState<DaysState>(emptyDayState());
  const [closures, setClosures] = useState<BusinessClosureResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<ToastState | null>(null);
  const [savingDay, setSavingDay] = useState<DayOfWeek | null>(null);

  const [closureForm, setClosureForm] = useState<BusinessClosureRequest>({ date: "", reason: "" });
  const [addingClosure, setAddingClosure] = useState(false);
  const [deletingClosureId, setDeletingClosureId] = useState<number | null>(null);

  useEffect(() => {
    if (businessId) fetchAll();
  }, [businessId]);

  async function fetchAll() {
    setLoading(true);
    setError(null);
    try {
      const [hoursRes, closuresRes] = await Promise.all([
        api.get<WorkingHourResponse[]>(`/api/businesses/${businessId}/working-hours`),
        api.get<BusinessClosureResponse[]>(`/api/businesses/${businessId}/closures`),
      ]);
      const merged = emptyDayState();
      for (const wh of hoursRes.data) {
        merged[wh.dayOfWeek] = {
          openTime: wh.openTime ? wh.openTime.slice(0, 5) : "",
          closeTime: wh.closeTime ? wh.closeTime.slice(0, 5) : "",
          closed: wh.closed,
          configured: true,
        };
      }
      setDays(merged);
      setClosures(closuresRes.data);
    } catch {
      setError("Çalışma saatleri yüklenirken hata oluştu.");
    } finally {
      setLoading(false);
    }
  }

  function updateDay(key: DayOfWeek, patch: Partial<DayState>) {
    setDays((prev) => ({ ...prev, [key]: { ...prev[key], ...patch } }));
  }

  async function saveDay(key: DayOfWeek) {
    const day = days[key];
    if (!day.closed && (!day.openTime || !day.closeTime)) {
      setToast({ message: "Açılış ve kapanış saati zorunlu (ya da günü kapalı işaretleyin).", type: "error" });
      return;
    }
    setSavingDay(key);
    try {
      const body: WorkingHourRequest = {
        dayOfWeek: key,
        openTime: day.closed ? null : day.openTime,
        closeTime: day.closed ? null : day.closeTime,
        closed: day.closed,
      };
      const res = await api.put<WorkingHourResponse>(`/api/businesses/${businessId}/working-hours`, body);
      updateDay(key, {
        openTime: res.data.openTime ? res.data.openTime.slice(0, 5) : "",
        closeTime: res.data.closeTime ? res.data.closeTime.slice(0, 5) : "",
        closed: res.data.closed,
        configured: true,
      });
      const dayLabel = DAYS.find((d) => d.key === key)?.label;
      setToast({ message: `✅ ${dayLabel} kaydedildi.`, type: "success" });
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Kaydedilirken hata oluştu."), type: "error" });
    } finally {
      setSavingDay(null);
    }
  }

  async function handleAddClosure(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setAddingClosure(true);
    try {
      const res = await api.post<BusinessClosureResponse>(`/api/businesses/${businessId}/closures`, closureForm);
      setClosures((prev) => [...prev, res.data]);
      setClosureForm({ date: "", reason: "" });
      setToast({ message: "✅ Kapanış günü eklendi.", type: "success" });
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Kapanış günü eklenirken hata oluştu."), type: "error" });
    } finally {
      setAddingClosure(false);
    }
  }

  async function handleDeleteClosure(closureId: number) {
    setDeletingClosureId(closureId);
    try {
      await api.delete(`/api/businesses/${businessId}/closures/${closureId}`);
      setClosures((prev) => prev.filter((c) => c.id !== closureId));
      setToast({ message: "🗑️ Kapanış günü kaldırıldı.", type: "success" });
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Kaldırılırken hata oluştu."), type: "error" });
    } finally {
      setDeletingClosureId(null);
    }
  }

  const timeInputClass =
    "px-2.5 py-1.5 bg-white border border-navy-200 rounded-lg text-slate-900 text-sm focus:outline-none focus:ring-2 focus:ring-brand/30 focus:border-brand/50 transition-all disabled:opacity-40";

  if (loading) {
    return (
      <div className="flex justify-center py-16">
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
        <p className="text-5xl mb-4">⚠️</p>
        <p className="text-red-500 text-lg mb-4">{error}</p>
        <button onClick={fetchAll} className="text-brand hover:text-brand-hover text-sm font-medium cursor-pointer">
          Tekrar Dene
        </button>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 space-y-8">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      {/* Haftalık çalışma saatleri */}
      <div>
        <h2 className="text-sm font-semibold text-slate-900 mb-3">Haftalık Çalışma Saatleri</h2>
        <div className="space-y-2">
          {DAYS.map((d) => {
            const day = days[d.key];
            return (
              <div
                key={d.key}
                className="flex flex-wrap items-center gap-3 bg-navy-50 border border-navy-100 rounded-xl px-4 py-3"
              >
                <span className="w-24 shrink-0 text-sm font-medium text-slate-900">{d.label}</span>

                <label className="flex items-center gap-1.5 text-xs text-slate-500 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={day.closed}
                    onChange={(e: ChangeEvent<HTMLInputElement>) => updateDay(d.key, { closed: e.target.checked })}
                    className="cursor-pointer accent-brand"
                  />
                  Kapalı
                </label>

                <input
                  type="time"
                  disabled={day.closed}
                  value={day.openTime}
                  onChange={(e: ChangeEvent<HTMLInputElement>) => updateDay(d.key, { openTime: e.target.value })}
                  className={timeInputClass}
                />
                <span className="text-slate-400 text-xs">—</span>
                <input
                  type="time"
                  disabled={day.closed}
                  value={day.closeTime}
                  onChange={(e: ChangeEvent<HTMLInputElement>) => updateDay(d.key, { closeTime: e.target.value })}
                  className={timeInputClass}
                />

                {!day.configured && (
                  <span className="text-xs text-slate-400 italic">yapılandırılmamış</span>
                )}

                <button
                  onClick={() => saveDay(d.key)}
                  disabled={savingDay === d.key || suspended}
                  className="ml-auto px-3 py-1.5 text-xs font-semibold text-white bg-brand hover:bg-brand-hover rounded-lg disabled:opacity-50 transition-all cursor-pointer"
                >
                  {savingDay === d.key ? "Kaydediliyor..." : "Kaydet"}
                </button>
              </div>
            );
          })}
        </div>
      </div>

      {/* Özel kapanış günleri */}
      <div>
        <h2 className="text-sm font-semibold text-slate-900 mb-3">Özel Kapanış Günleri</h2>

        <form onSubmit={handleAddClosure} className="flex flex-wrap gap-3 mb-4">
          <input
            type="date"
            required
            min={new Date().toISOString().slice(0, 10)}
            value={closureForm.date}
            onChange={(e: ChangeEvent<HTMLInputElement>) => setClosureForm({ ...closureForm, date: e.target.value })}
            className="px-3 py-2 bg-white border border-navy-200 rounded-lg text-slate-900 text-sm focus:outline-none focus:ring-2 focus:ring-brand/30 focus:border-brand/50 transition-all"
          />
          <input
            type="text"
            placeholder="Sebep (opsiyonel — ör. Resmi Tatil)"
            value={closureForm.reason ?? ""}
            onChange={(e: ChangeEvent<HTMLInputElement>) => setClosureForm({ ...closureForm, reason: e.target.value })}
            className="flex-1 min-w-[180px] px-3 py-2 bg-white border border-navy-200 rounded-lg text-slate-900 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand/30 focus:border-brand/50 transition-all"
          />
          <button
            type="submit"
            disabled={addingClosure || suspended}
            className="px-4 py-2 text-sm font-semibold text-white bg-brand hover:bg-brand-hover rounded-lg disabled:opacity-50 transition-all cursor-pointer"
          >
            {addingClosure ? "Ekleniyor..." : "Ekle"}
          </button>
        </form>

        {closures.length === 0 ? (
          <p className="text-slate-400 text-sm">Planlanmış özel kapanış günü yok.</p>
        ) : (
          <div className="space-y-2">
            {closures.map((c) => (
              <div
                key={c.id}
                className="flex items-center justify-between bg-navy-50 border border-navy-100 rounded-xl px-4 py-3"
              >
                <div>
                  <span className="text-slate-900 text-sm font-medium">{c.date}</span>
                  {c.reason && <span className="text-slate-500 text-xs ml-2">— {c.reason}</span>}
                </div>
                <button
                  onClick={() => handleDeleteClosure(c.id)}
                  disabled={deletingClosureId === c.id || suspended}
                  className="px-3 py-1.5 text-xs font-medium text-red-600 hover:text-red-700 border border-red-200 hover:border-red-300 rounded-lg disabled:opacity-50 transition-all cursor-pointer"
                >
                  {deletingClosureId === c.id ? "Siliniyor..." : "Sil"}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
