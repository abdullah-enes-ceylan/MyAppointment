import { useState, type ChangeEvent, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowLeft, Eye, EyeOff, KeyRound, Lock, Mail } from "lucide-react";
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
  const [showPassword, setShowPassword] = useState(false);
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
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center p-4 bg-slate-50">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      <div className="w-full max-w-md">
        {/* Navbar bu sayfada bilerek gizli (bkz. Navbar.tsx isLoginPage) --
            yerine dikkat dagitmayan bu minimal ust satir geliyor. */}
        <div className="flex items-center justify-between mb-4 px-1">
          <Link
            to="/"
            className="flex items-center gap-1.5 text-sm font-medium text-slate-500 hover:text-slate-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Keşfet'e Dön
          </Link>
          <span className="text-xs text-slate-400">Randevum • Giriş</span>
        </div>

        <div className="bg-white rounded-3xl shadow-xl border border-slate-200/80 overflow-hidden">
          {/* Header */}
          <div className="bg-gradient-to-b from-[#0a1420] via-brand-mid to-brand px-8 py-7 text-center">
            <div className="w-12 h-12 bg-white/10 rounded-2xl flex items-center justify-center mx-auto mb-3">
              <KeyRound className="w-6 h-6 text-white" />
            </div>
            <h1 className="text-xl font-bold text-white">Giriş Yap</h1>
            <p className="text-slate-300 text-sm mt-1">Randevularınızı yönetmek için hesabınıza erişin</p>

            {/* Giriş/Kayıt sekmesi -- ikisi de ayrı sayfa (route), burası
                sadece görsel bir segmented control; "Kayıt Ol" tıklanınca
                /register'a gider. */}
            <div className="mt-5 grid grid-cols-2 gap-1 bg-white/10 rounded-xl p-1">
              <span className="py-2 rounded-lg bg-white text-brand text-sm font-semibold text-center">
                Giriş Yap
              </span>
              <Link
                to="/register"
                className="py-2 rounded-lg text-slate-300 text-sm font-semibold text-center hover:bg-white/10 transition-colors"
              >
                Kayıt Ol
              </Link>
            </div>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="p-8 space-y-5">
            <div>
              <label htmlFor="email" className="block text-xs font-semibold text-slate-500 mb-1.5 tracking-wide">
                E-POSTA
              </label>
              <div className="relative">
                <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                <input
                  id="email"
                  name="email"
                  type="email"
                  required
                  value={form.email}
                  onChange={handleChange}
                  placeholder="ahmet@example.com"
                  className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand/30 focus:border-brand/50 transition-all"
                />
              </div>
            </div>

            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label htmlFor="password" className="block text-xs font-semibold text-slate-500 tracking-wide">
                  ŞİFRE
                </label>
                {/* Şifre sıfırlama akışı henüz yok (bkz. NOTLAR.md) --
                    Navbar'daki bildirim zilinde de kullanılan aynı desen:
                    var olmayan bir özelliği çalışıyormuş gibi göstermek
                    yerine bilerek pasif + "yakında" ipucu. */}
                <span
                  title="Bu özellik yakında eklenecek"
                  className="text-xs font-medium text-slate-300 cursor-not-allowed select-none"
                >
                  Şifremi Unuttum?
                </span>
              </div>
              <div className="relative">
                <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                <input
                  id="password"
                  name="password"
                  type={showPassword ? "text" : "password"}
                  required
                  value={form.password}
                  onChange={handleChange}
                  placeholder="••••••••"
                  className="w-full pl-10 pr-11 py-3 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand/30 focus:border-brand/50 transition-all"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 cursor-pointer"
                  aria-label={showPassword ? "Şifreyi gizle" : "Şifreyi göster"}
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            {/* Oturum şu an her zaman kalıcı (JWT localStorage'da, bkz.
                AuthContext) -- ayrı bir "hatırlama" tercihi henüz yok, bu
                yüzden kutu işaretli ve devre dışı: gerçekte olmayan bir
                seçim sunmuyoruz. */}
            <label className="flex items-center gap-2 text-sm text-slate-600 cursor-not-allowed select-none">
              <input type="checkbox" checked disabled className="w-4 h-4 rounded accent-brand" />
              <span title="Oturumun açık kalması şu an varsayılan davranış">Beni Hatırla</span>
            </label>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3.5 bg-brand hover:bg-brand-hover text-white font-semibold text-sm rounded-xl shadow-lg shadow-brand/20 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-300 transform hover:scale-[1.02] active:scale-[0.98] cursor-pointer"
            >
              {loading ? "Giriş yapılıyor..." : "Giriş Yap"}
            </button>

            <p className="text-center text-sm text-slate-500">
              Hesabınız yok mu?{" "}
              <Link to="/register" className="text-brand hover:text-brand-hover font-semibold transition-colors">
                Kayıt Ol
              </Link>
            </p>
          </form>
        </div>
      </div>
    </div>
  );
}
