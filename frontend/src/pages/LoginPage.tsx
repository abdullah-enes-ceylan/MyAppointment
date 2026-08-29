import { useState, type ChangeEvent, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import api from "../api/axios";
import { getErrorMessage } from "../api/errors";
import Toast from "../components/Toast";
import { useAuth } from "../context/AuthContext";
import type { LoginRequest, LoginResponse } from "../types/api";

interface ToastState {
  message: string;
  type: "success" | "error";
}

export default function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [toast, setToast] = useState<ToastState | null>(null);
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<LoginRequest>({
    email: "",
    password: "",
  });

  function handleChange(e: ChangeEvent<HTMLInputElement>) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setLoading(true);

    try {
      const res = await api.post<LoginResponse>("/api/auth/login", form);
      // login() (AuthContext) hem localStorage'a yazar hem React state'ini
      // gunceller — eskiden burada window.location.reload() vardi, artik
      // Navbar gibi bilesenler sayfa yenilenmeden otomatik guncelleniyor.
      login(res.data.token);
      setToast({ message: "Giriş başarılı! Yönlendiriliyorsunuz...", type: "success" });
      setTimeout(() => {
        navigate("/");
      }, 800);
    } catch (err) {
      // err.response?.data bazen duz metin (401 — hatali sifre), bazen
      // yapilandirilmis bir nesne (400 — ValidationErrorResponse). Once
      // .message'a bak, o yoksa duz veriyi kullan — aksi halde
      // ValidationErrorResponse dondugunde ekranda "[object Object]" cikardi.
      setToast({ message: getErrorMessage(err, "Hatalı e-posta veya şifre!"), type: "error" });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center p-4">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      <div className="w-full max-w-md">
        <div className="absolute -inset-1 bg-gradient-to-r from-emerald-500/10 via-teal-500/10 to-cyan-500/10 rounded-3xl blur-xl pointer-events-none" />

        <div className="relative bg-surface/80 backdrop-blur-xl border border-white/10 rounded-3xl shadow-2xl overflow-hidden">
          {/* Header */}
          <div className="bg-gradient-to-r from-emerald-600 to-teal-600 px-8 py-6 text-center">
            <div className="w-14 h-14 bg-white/20 rounded-2xl flex items-center justify-center mx-auto mb-3 backdrop-blur-sm">
              <span className="text-3xl">🔐</span>
            </div>
            <h1 className="text-2xl font-bold text-white">Giriş Yap</h1>
            <p className="text-emerald-100 text-sm mt-1">Hesabınıza erişin</p>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="p-8 space-y-5">
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
                className="w-full px-4 py-3 bg-bg-light border border-white/10 rounded-xl text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500/50 transition-all"
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
                className="w-full px-4 py-3 bg-bg-light border border-white/10 rounded-xl text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500/50 transition-all"
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3.5 bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white font-semibold text-sm rounded-xl shadow-lg shadow-emerald-500/25 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-300 transform hover:scale-[1.02] active:scale-[0.98] cursor-pointer"
            >
              {loading ? "Giriş yapılıyor..." : "Giriş Yap"}
            </button>

            <p className="text-center text-sm text-slate-400">
              Hesabınız yok mu?{" "}
              <Link to="/register" className="text-emerald-400 hover:text-emerald-300 font-medium transition-colors">
                Kayıt Ol
              </Link>
            </p>
          </form>
        </div>
      </div>
    </div>
  );
}
