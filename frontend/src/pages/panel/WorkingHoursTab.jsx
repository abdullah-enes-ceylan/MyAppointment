import { useState, useEffect } from "react";
import api from "../../api/axios";
import Toast from "../../components/Toast";

const DAYS = [
  { key: "MONDAY", label: "Pazartesi" },
  { key: "TUESDAY", label: "Salı" },
  { key: "WEDNESDAY", label: "Çarşamba" },
  { key: "THURSDAY", label: "Perşembe" },
  { key: "FRIDAY", label: "Cuma" },
  { key: "SATURDAY", label: "Cumartesi" },
  { key: "SUNDAY", label: "Pazar" },
];

function emptyDayState() {
  const state = {};
  for (const d of DAYS) {
    state[d.key] = { openTime: "", closeTime: "", closed: false, configured: false };
  }
  return state;
}

// HTML <input type="time"> "HH:mm" verir, backend'deki LocalTime alanı
// (Jackson ISO_LOCAL_TIME) saniyesiz "HH:mm"i de kabul ediyor — ayrıca
// dönüştürmeye gerek yok.
export default function WorkingHoursTab({ businessId }) {
  const [days, setDays] = useState(emptyDayState());
  const [closures, setClosures] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [toast, setToast] = useState(null);
  const [savingDay, setSavingDay] = useState(null);

  const [closureForm, setClosureForm] = useState({ date: "", reason: "" });
  const [addingClosure, setAddingClosure] = useState(false);
  const [deletingClosureId, setDeletingClosureId] = useState(null);

  useEffect(() => {
    if (businessId) fetchAll();
  }, [businessId]);

  function extractError(err, fallback) {
    return String(err.response?.data?.message || err.response?.data || fallback);
  }

  async function fetchAll() {
    setLoading(true);
    setError(null);
    try {
      const [hoursRes, closuresRes] = await Promise.all([
        api.get(`/api/businesses/${businessId}/working-hours`),
        api.get(`/api/businesses/${businessId}/closures`),
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
    } catch (err) {
      setError("Çalışma saatleri yüklenirken hata oluştu.");
    } finally {
      setLoading(false);
    }
  }

  function updateDay(key, patch) {
    setDays((prev) => ({ ...prev, [key]: { ...prev[key], ...patch } }));
  }

  async function saveDay(key) {
    const day = days[key];
    if (!day.closed && (!day.openTime || !day.closeTime)) {
      setToast({ message: "Açılış ve kapanış saati zorunlu (ya da günü kapalı işaretleyin).", type: "error" });
      return;
    }
    setSavingDay(key);
    try {
      const res = await api.put(`/api/businesses/${businessId}/working-hours`, {
        dayOfWeek: key,
        openTime: day.closed ? null : day.openTime,
        closeTime: day.closed ? null : day.closeTime,
        closed: day.closed,
      });
      updateDay(key, {
        openTime: res.data.openTime ? res.data.openTime.slice(0, 5) : "",
        closeTime: res.data.closeTime ? res.data.closeTime.slice(0, 5) : "",
        closed: res.data.closed,
        configured: true,
      });
      setToast({ message: `✅ ${DAYS.find((d) => d.key === key).label} kaydedildi.`, type: "success" });
    } catch (err) {
      setToast({ message: extractError(err, "Kaydedilirken hata oluştu."), type: "error" });
    } finally {
      setSavingDay(null);
    }
  }

  async function handleAddClosure(e) {
    e.preventDefault();
    setAddingClosure(true);
    try {
      const res = await api.post(`/api/businesses/${businessId}/closures`, closureForm);
      setClosures((prev) => [...prev, res.data]);
      setClosureForm({ date: "", reason: "" });
      setToast({ message: "✅ Kapanış günü eklendi.", type: "success" });
    } catch (err) {
      setToast({ message: extractError(err, "Kapanış günü eklenirken hata oluştu."), type: "error" });
    } finally {
      setAddingClosure(false);
    }
  }

  async function handleDeleteClosure(closureId) {
    setDeletingClosureId(closureId);
    try {
      await api.delete(`/api/businesses/${businessId}/closures/${closureId}`);
      setClosures((prev) => prev.filter((c) => c.id !== closureId));
      setToast({ message: "🗑️ Kapanış günü kaldırıldı.", type: "success" });
    } catch (err) {
      setToast({ message: extractError(err, "Kaldırılırken hata oluştu."), type: "error" });
    } finally {
      setDeletingClosureId(null);
    }
  }

  const timeInputClass =
    "px-2.5 py-1.5 bg-bg-light border border-white/10 rounded-lg text-white text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500/50 transition-all disabled:opacity-40";

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
        <p className="text-red-400 text-lg mb-4">{error}</p>
        <button onClick={fetchAll} className="text-emerald-400 hover:text-emerald-300 text-sm font-medium cursor-pointer">
          Tekrar Dene
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      {/* Haftalık çalışma saatleri */}
      <div>
        <h2 className="text-sm font-semibold text-white mb-3">Haftalık Çalışma Saatleri</h2>
        <div className="space-y-2">
          {DAYS.map((d) => {
            const day = days[d.key];
            return (
              <div
                key={d.key}
                className="flex flex-wrap items-center gap-3 bg-surface/80 border border-white/10 rounded-xl px-4 py-3"
              >
                <span className="w-24 shrink-0 text-sm font-medium text-white">{d.label}</span>

                <label className="flex items-center gap-1.5 text-xs text-slate-400 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={day.closed}
                    onChange={(e) => updateDay(d.key, { closed: e.target.checked })}
                    className="cursor-pointer"
                  />
                  Kapalı
                </label>

                <input
                  type="time"
                  disabled={day.closed}
                  value={day.openTime}
                  onChange={(e) => updateDay(d.key, { openTime: e.target.value })}
                  className={timeInputClass}
                />
                <span className="text-slate-500 text-xs">—</span>
                <input
                  type="time"
                  disabled={day.closed}
                  value={day.closeTime}
                  onChange={(e) => updateDay(d.key, { closeTime: e.target.value })}
                  className={timeInputClass}
                />

                {!day.configured && (
                  <span className="text-xs text-slate-500 italic">yapılandırılmamış</span>
                )}

                <button
                  onClick={() => saveDay(d.key)}
                  disabled={savingDay === d.key}
                  className="ml-auto px-3 py-1.5 text-xs font-semibold text-white bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 rounded-lg disabled:opacity-50 transition-all cursor-pointer"
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
        <h2 className="text-sm font-semibold text-white mb-3">Özel Kapanış Günleri</h2>

        <form onSubmit={handleAddClosure} className="flex flex-wrap gap-3 mb-4">
          <input
            type="date"
            required
            min={new Date().toISOString().slice(0, 10)}
            value={closureForm.date}
            onChange={(e) => setClosureForm({ ...closureForm, date: e.target.value })}
            className="px-3 py-2 bg-bg-light border border-white/10 rounded-lg text-white text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500/50 transition-all"
          />
          <input
            type="text"
            placeholder="Sebep (opsiyonel — ör. Resmi Tatil)"
            value={closureForm.reason}
            onChange={(e) => setClosureForm({ ...closureForm, reason: e.target.value })}
            className="flex-1 min-w-[180px] px-3 py-2 bg-bg-light border border-white/10 rounded-lg text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500/50 transition-all"
          />
          <button
            type="submit"
            disabled={addingClosure}
            className="px-4 py-2 text-sm font-semibold text-white bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 rounded-lg disabled:opacity-50 transition-all cursor-pointer"
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
                className="flex items-center justify-between bg-surface/80 border border-white/10 rounded-xl px-4 py-3"
              >
                <div>
                  <span className="text-white text-sm font-medium">{c.date}</span>
                  {c.reason && <span className="text-slate-400 text-xs ml-2">— {c.reason}</span>}
                </div>
                <button
                  onClick={() => handleDeleteClosure(c.id)}
                  disabled={deletingClosureId === c.id}
                  className="px-3 py-1.5 text-xs font-medium text-red-400 hover:text-red-300 border border-red-500/20 hover:border-red-500/40 rounded-lg disabled:opacity-50 transition-all cursor-pointer"
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
