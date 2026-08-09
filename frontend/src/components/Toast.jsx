import { useEffect } from "react";

export default function Toast({ message, type = "success", onClose }) {
  useEffect(() => {
    const timer = setTimeout(() => onClose(), 4000);
    return () => clearTimeout(timer);
  }, [onClose]);

  return (
    <div
      className={`fixed top-6 right-6 z-50 flex items-center gap-3 px-5 py-4 rounded-2xl border backdrop-blur-sm shadow-2xl text-white animate-slide-in ${
        type === "success"
          ? "bg-emerald-500/90 border-emerald-400"
          : "bg-red-500/90 border-red-400"
      }`}
    >
      <span className="text-xl">{type === "success" ? "✅" : "❌"}</span>
      <p className="text-sm font-medium">{message}</p>
      <button
        onClick={onClose}
        className="ml-2 text-white/70 hover:text-white transition-colors cursor-pointer"
      >
        ✕
      </button>

      {/* Inline keyframe for slide-in animation */}
      <style>{`
        @keyframes slide-in {
          from { opacity: 0; transform: translateX(100px); }
          to { opacity: 1; transform: translateX(0); }
        }
        .animate-slide-in {
          animation: slide-in 0.4s cubic-bezier(0.16, 1, 0.3, 1);
        }
      `}</style>
    </div>
  );
}
