import { useState } from "react";
import "./App.css";

const API_BASE = "http://localhost:8080/api/appointments";

function formatTime(timeStr) {
  // "09:45:00" → "09:45"
  const parts = timeStr.split(":");
  return `${parts[0]}:${parts[1]}`;
}

function Toast({ message, type, onClose }) {
  const bgColor =
    type === "success"
      ? "bg-emerald-500/90 border-emerald-400"
      : "bg-red-500/90 border-red-400";

  return (
    <div
      className={`fixed top-6 right-6 z-50 flex items-center gap-3 px-5 py-4 rounded-2xl border backdrop-blur-sm shadow-2xl text-white animate-slide-in ${bgColor}`}
    >
      <span className="text-xl">{type === "success" ? "✅" : "❌"}</span>
      <p className="text-sm font-medium">{message}</p>
      <button
        onClick={onClose}
        className="ml-2 text-white/70 hover:text-white transition-colors cursor-pointer"
      >
        ✕
      </button>
    </div>
  );
}

function App() {
  const [selectedDate, setSelectedDate] = useState("");
  const [slots, setSlots] = useState([]);
  const [loading, setLoading] = useState(false);
  const [bookingSlot, setBookingSlot] = useState(null);
  const [toast, setToast] = useState(null);

  function showToast(message, type = "success") {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  }

  async function fetchAvailableSlots() {
    if (!selectedDate) {
      showToast("Lütfen bir tarih seçin.", "error");
      return;
    }

    setLoading(true);
    setSlots([]);

    try {
      const res = await fetch(
        `${API_BASE}/available-slots?businessId=1&serviceId=1&date=${selectedDate}`
      );

      if (!res.ok) {
        throw new Error(`Sunucu hatası: ${res.status}`);
      }

      const data = await res.json();
      setSlots(data);

      if (data.length === 0) {
        showToast("Bu tarihte uygun saat bulunamadı.", "error");
      }
    } catch (err) {
      showToast(err.message || "Saatler alınırken bir hata oluştu.", "error");
    } finally {
      setLoading(false);
    }
  }

  async function bookAppointment(time) {
    setBookingSlot(time);

    try {
      const res = await fetch(`${API_BASE}/create`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          customerId: 1,
          businessId: 1,
          serviceId: 1,
          appointmentDate: `${selectedDate}T${time}`,
        }),
      });

      if (!res.ok) {
        const errorText = await res.text();
        throw new Error(errorText || `Sunucu hatası: ${res.status}`);
      }

      showToast("🎉 Randevu Başarıyla Oluşturuldu!", "success");

      // Refresh available slots
      const refreshRes = await fetch(
        `${API_BASE}/available-slots?businessId=1&serviceId=1&date=${selectedDate}`
      );
      if (refreshRes.ok) {
        const updatedSlots = await refreshRes.json();
        setSlots(updatedSlots);
      }
    } catch (err) {
      showToast(err.message || "Randevu oluşturulurken hata oluştu.", "error");
    } finally {
      setBookingSlot(null);
    }
  }

  return (
    <div className="min-h-screen bg-bg font-sans flex items-center justify-center p-4">
      {/* Animated Background Blobs */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none">
        <div className="absolute -top-40 -left-40 w-80 h-80 bg-emerald-500/10 rounded-full blur-3xl animate-pulse" />
        <div className="absolute -bottom-40 -right-40 w-96 h-96 bg-teal-500/10 rounded-full blur-3xl animate-pulse delay-1000" />
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-64 h-64 bg-cyan-500/5 rounded-full blur-3xl animate-pulse delay-500" />
      </div>

      {/* Toast Notification */}
      {toast && (
        <Toast
          message={toast.message}
          type={toast.type}
          onClose={() => setToast(null)}
        />
      )}

      {/* Main Card */}
      <div className="relative w-full max-w-lg">
        {/* Glow effect behind card */}
        <div className="absolute -inset-1 bg-gradient-to-r from-emerald-500/20 via-teal-500/20 to-cyan-500/20 rounded-3xl blur-xl" />

        <div className="relative bg-surface/80 backdrop-blur-xl border border-white/10 rounded-3xl shadow-2xl overflow-hidden">
          {/* Header */}
          <div className="bg-gradient-to-r from-emerald-600 to-teal-600 px-8 py-6">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-white/20 rounded-xl flex items-center justify-center backdrop-blur-sm">
                <span className="text-xl">📅</span>
              </div>
              <div>
                <h1 className="text-xl font-bold text-white tracking-tight">
                  Randevum
                </h1>
                <p className="text-emerald-100 text-xs font-light">
                  Online Randevu Yönetim Sistemi
                </p>
              </div>
            </div>
          </div>

          {/* Body */}
          <div className="p-8 space-y-6">
            {/* Date Picker */}
            <div className="space-y-2">
              <label
                htmlFor="date-picker"
                className="block text-sm font-medium text-slate-300"
              >
                📆 Tarih Seçin
              </label>
              <input
                id="date-picker"
                type="date"
                value={selectedDate}
                onChange={(e) => {
                  setSelectedDate(e.target.value);
                  setSlots([]);
                }}
                className="w-full px-4 py-3 bg-bg-light border border-white/10 rounded-xl text-white text-sm
                           focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500/50
                           transition-all duration-300 hover:border-white/20"
              />
            </div>

            {/* Fetch Button */}
            <button
              id="fetch-slots-btn"
              onClick={fetchAvailableSlots}
              disabled={loading || !selectedDate}
              className="w-full py-3.5 bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500
                         text-white font-semibold text-sm rounded-xl shadow-lg shadow-emerald-500/25
                         disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:from-emerald-600 disabled:hover:to-teal-600
                         transition-all duration-300 transform hover:scale-[1.02] active:scale-[0.98] cursor-pointer"
            >
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <svg
                    className="animate-spin h-4 w-4"
                    viewBox="0 0 24 24"
                    fill="none"
                  >
                    <circle
                      className="opacity-25"
                      cx="12"
                      cy="12"
                      r="10"
                      stroke="currentColor"
                      strokeWidth="4"
                    />
                    <path
                      className="opacity-75"
                      fill="currentColor"
                      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                    />
                  </svg>
                  Yükleniyor...
                </span>
              ) : (
                "🔍 Boş Saatleri Getir"
              )}
            </button>

            {/* Time Slots */}
            {slots.length > 0 && (
              <div className="space-y-3 animate-fade-in">
                <div className="flex items-center gap-2">
                  <div className="h-px flex-1 bg-gradient-to-r from-transparent via-white/10 to-transparent" />
                  <span className="text-xs font-medium text-slate-400 uppercase tracking-widest">
                    Uygun Saatler
                  </span>
                  <div className="h-px flex-1 bg-gradient-to-r from-transparent via-white/10 to-transparent" />
                </div>

                <div className="grid grid-cols-3 gap-3">
                  {slots.map((slot) => {
                    const isBooking = bookingSlot === slot;
                    return (
                      <button
                        key={slot}
                        onClick={() => bookAppointment(slot)}
                        disabled={isBooking}
                        className="group relative py-3 px-2 bg-emerald-500/10 border border-emerald-500/30 rounded-xl
                                   text-emerald-400 font-semibold text-sm
                                   hover:bg-emerald-500/20 hover:border-emerald-400/50 hover:text-emerald-300 hover:shadow-lg hover:shadow-emerald-500/10
                                   disabled:opacity-50 disabled:cursor-not-allowed
                                   transition-all duration-300 transform hover:scale-105 active:scale-95 cursor-pointer"
                      >
                        {isBooking ? (
                          <svg
                            className="animate-spin h-4 w-4 mx-auto"
                            viewBox="0 0 24 24"
                            fill="none"
                          >
                            <circle
                              className="opacity-25"
                              cx="12"
                              cy="12"
                              r="10"
                              stroke="currentColor"
                              strokeWidth="4"
                            />
                            <path
                              className="opacity-75"
                              fill="currentColor"
                              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                            />
                          </svg>
                        ) : (
                          <>
                            <span className="relative z-10">
                              🕐 {formatTime(slot)}
                            </span>
                            <div className="absolute inset-0 bg-gradient-to-r from-emerald-500/0 to-teal-500/0 group-hover:from-emerald-500/10 group-hover:to-teal-500/10 rounded-xl transition-all duration-300" />
                          </>
                        )}
                      </button>
                    );
                  })}
                </div>

                <p className="text-center text-xs text-slate-500 mt-2">
                  Bir saate tıklayarak randevunuzu oluşturabilirsiniz
                </p>
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="px-8 py-4 bg-white/[0.02] border-t border-white/5">
            <p className="text-center text-xs text-slate-500">
              Randevum © 2026 — Tüm hakları saklıdır
            </p>
          </div>
        </div>
      </div>

      {/* Custom Animations */}
      <style>{`
        @keyframes slide-in {
          from {
            opacity: 0;
            transform: translateX(100px);
          }
          to {
            opacity: 1;
            transform: translateX(0);
          }
        }
        @keyframes fade-in {
          from {
            opacity: 0;
            transform: translateY(10px);
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }
        .animate-slide-in {
          animation: slide-in 0.4s cubic-bezier(0.16, 1, 0.3, 1);
        }
        .animate-fade-in {
          animation: fade-in 0.5s cubic-bezier(0.16, 1, 0.3, 1);
        }
        .delay-500 {
          animation-delay: 500ms;
        }
        .delay-1000 {
          animation-delay: 1000ms;
        }
        input[type="date"]::-webkit-calendar-picker-indicator {
          filter: invert(1) brightness(0.8);
          cursor: pointer;
        }
      `}</style>
    </div>
  );
}

export default App;
