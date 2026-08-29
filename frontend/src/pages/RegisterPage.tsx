import { useState, type ChangeEvent, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import api from "../api/axios";
import { getErrorMessage } from "../api/errors";
import Toast from "../components/Toast";
import type { RegisterRequest } from "../types/api";

interface ToastState {
  message: string;
  type: "success" | "error";
}

export default function RegisterPage() {
  const navigate = useNavigate();
  const [toast, setToast] = useState<ToastState | null>(null);
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<RegisterRequest>({
    name: "",
    surName: "",
    email: "",
    password: "",
    phone: "",
  });

  function handleChange(e: ChangeEvent<HTMLInputElement>) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setLoading(true);

    try {
      await api.post("/api/users/register", form);
      setToast({ message: "🎉 Kayıt başarılı! Giriş sayfasına yönlendiriliyorsunuz...", type: "success" });
      setTimeout(() => navigate("/login"), 1500);
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Kayıt sırasında bir hata oluştu."), type: "error" });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center p-4">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      <div className="w-full max-w-md">
        {/* Glow */}
        <div className="absolute -inset-1 bg-gradient-to-r from-emerald-500/10 via-teal-500/10 to-cyan-500/10 rounded-3xl blur-xl pointer-events-none" />

        <div className="relative bg-surface/80 backdrop-blur-xl border border-white/10 rounded-3xl shadow-2xl overflow-hidden">
          {/* Header */}
          <div className="bg-gradient-to-r from-emerald-600 to-teal-600 px-8 py-6 text-center">
            <h1 className="text-2xl font-bold text-white">Hesap Oluştur</h1>
            <p className="text-emerald-100 text-sm mt-1">Randevum'a hoş geldiniz</p>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="p-8 space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label htmlFor="name" className="block text-xs font-medium text-slate-400 mb-1.5">Ad</label>
                <input
                  id="name"
                  name="name"
                  type="text"
                  required
                  value={form.name}
                  onChange={handleChange}
                  placeholder="Ahmet"
                  className="w-full px-4 py-2.5 bg-bg-light border border-white/10 rounded-xl text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500/50 transition-all"
                />
              </div>
              <div>
                <label htmlFor="surName" className="block text-xs font-medium text-slate-400 mb-1.5">Soyad</label>
                <input
                  id="surName"
                  name="surName"
                  type="text"
                  required
                  value={form.surName}
                  onChange={handleChange}
                  placeholder="Yılmaz"
                  className="w-full px-4 py-2.5 bg-bg-light border border-white/10 rounded-xl text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500/50 transition-all"
                />
              </div>
            </div>

            <div>
              <label htmlFor="email" className="block text-xs font-medium text-slate-400 mb-1.5">E-posta</label>
              <input
                id="email"
                name="email"
                type="email"
                required
                value={form.email}
                onChange={handleChange}
                placeholder="ahmet@example.com"
                className="w-full px-4 py-2.5 bg-bg-light border border-white/10 rounded-xl text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500/50 transition-all"
              />
            </div>

            <div>
              <label htmlFor="password" className="block text-xs font-medium text-slate-400 mb-1.5">Şifre</label>
              <input
                id="password"
                name="password"
                type="password"
                required
                value={form.password}
                onChange={handleChange}
                placeholder="••••••••"
                className="w-full px-4 py-2.5 bg-bg-light border border-white/10 rounded-xl text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500/50 transition-all"
              />
            </div>

            <div>
              <label htmlFor="phone" className="block text-xs font-medium text-slate-400 mb-1.5">Telefon</label>
              <input
                id="phone"
                name="phone"
                type="tel"
                required
                value={form.phone}
                onChange={handleChange}
                placeholder="05XX XXX XX XX"
                className="w-full px-4 py-2.5 bg-bg-light border border-white/10 rounded-xl text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500/50 transition-all"
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 mt-2 bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-semibold text-sm rounded-xl shadow-lg shadow-emerald-500/25 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-300 transform hover:scale-[1.02] active:scale-[0.98] cursor-pointer"
            >
              {loading ? "Kaydediliyor..." : "Kayıt Ol"}
            </button>

            <p className="text-center text-sm text-slate-400 mt-4">
              Zaten hesabınız var mı?{" "}
              <Link to="/login" className="text-emerald-400 hover:text-emerald-300 font-medium transition-colors">
                Giriş Yap
              </Link>
            </p>
          </form>
        </div>
      </div>
    </div>
  );
}
