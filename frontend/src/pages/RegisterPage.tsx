import { useState, type ChangeEvent, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowLeft, Eye, EyeOff, Lock, Mail, Phone, User, UserPlus } from "lucide-react";
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
  const [showPassword, setShowPassword] = useState(false);
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

  // Backend'deki @Pattern (RegisterRequest.phone) ile AYNI format: "05" +
  // 9 hane, 11 karakter. Rakam dışı her şey (boşluk, harf, "+90" vb.) yazarken
  // ELENIR -- kullanıcı sınırsız/karışık karakter yapıştıramaz. Asıl güvenlik
  // sınırı backend'de (bu sadece kullanıcı deneyimi, curl ile atlanabilir).
  function handlePhoneChange(e: ChangeEvent<HTMLInputElement>) {
    const digitsOnly = e.target.value.replace(/\D/g, "").slice(0, 11);
    setForm({ ...form, phone: digitsOnly });
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
    <div className="min-h-[calc(100vh-4rem)] flex items-center justify-center p-4 bg-slate-50">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      <div className="w-full max-w-md">
        {/* Navbar bu sayfada bilerek gizli (bkz. Navbar.tsx isLoginPage) --
            LoginPage'deki ayni minimal ust satir. */}
        <div className="flex items-center justify-between mb-4 px-1">
          <Link
            to="/"
            className="flex items-center gap-1.5 text-sm font-medium text-slate-500 hover:text-slate-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Keşfet'e Dön
          </Link>
          <span className="text-xs text-slate-400">Randevum • Kayıt</span>
        </div>

        <div className="bg-white rounded-3xl shadow-xl border border-slate-200/80 overflow-hidden">
          {/* Header -- LoginPage ile AYNI acik tema kabuk, sadece basliklar farkli. */}
          <div className="bg-gradient-to-b from-[#0a1420] via-brand-mid to-brand px-8 py-7 text-center">
            <div className="w-12 h-12 bg-white/10 rounded-2xl flex items-center justify-center mx-auto mb-3">
              <UserPlus className="w-6 h-6 text-white" />
            </div>
            <h1 className="text-xl font-bold text-white">Hesap Oluştur</h1>
            <p className="text-slate-300 text-sm mt-1">Randevum'a hoş geldiniz</p>

            {/* Giriş/Kayıt sekmesi -- LoginPage'deki ile AYNI segmented control,
                burada "Kayıt Ol" aktif taraf. */}
            <div className="mt-5 grid grid-cols-2 gap-1 bg-white/10 rounded-xl p-1">
              <Link
                to="/login"
                className="py-2 rounded-lg text-slate-300 text-sm font-semibold text-center hover:bg-white/10 transition-colors"
              >
                Giriş Yap
              </Link>
              <span className="py-2 rounded-lg bg-white text-brand text-sm font-semibold text-center">
                Kayıt Ol
              </span>
            </div>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="p-8 space-y-5">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label htmlFor="name" className="block text-xs font-semibold text-slate-500 mb-1.5 tracking-wide">
                  AD
                </label>
                <div className="relative">
                  <User className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                  <input
                    id="name"
                    name="name"
                    type="text"
                    required
                    value={form.name}
                    onChange={handleChange}
                    placeholder="Ahmet"
                    className="w-full pl-10 pr-3 py-3 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand/30 focus:border-brand/50 transition-all"
                  />
                </div>
              </div>
              <div>
                <label htmlFor="surName" className="block text-xs font-semibold text-slate-500 mb-1.5 tracking-wide">
                  SOYAD
                </label>
                <div className="relative">
                  <User className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                  <input
                    id="surName"
                    name="surName"
                    type="text"
                    required
                    value={form.surName}
                    onChange={handleChange}
                    placeholder="Yılmaz"
                    className="w-full pl-10 pr-3 py-3 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand/30 focus:border-brand/50 transition-all"
                  />
                </div>
              </div>
            </div>

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
              <label htmlFor="password" className="block text-xs font-semibold text-slate-500 mb-1.5 tracking-wide">
                ŞİFRE
              </label>
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

            <div>
              <label htmlFor="phone" className="block text-xs font-semibold text-slate-500 mb-1.5 tracking-wide">
                TELEFON
              </label>
              <div className="relative">
                <Phone className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                <input
                  id="phone"
                  name="phone"
                  type="tel"
                  inputMode="numeric"
                  maxLength={11}
                  required
                  value={form.phone}
                  onChange={handlePhoneChange}
                  placeholder="05XX XXX XX XX"
                  className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand/30 focus:border-brand/50 transition-all"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3.5 bg-brand hover:bg-brand-hover text-white font-semibold text-sm rounded-xl shadow-lg shadow-brand/20 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-300 transform hover:scale-[1.02] active:scale-[0.98] cursor-pointer"
            >
              {loading ? "Kaydediliyor..." : "Kayıt Ol"}
            </button>

            <p className="text-center text-sm text-slate-500">
              Zaten hesabınız var mı?{" "}
              <Link to="/login" className="text-brand hover:text-brand-hover font-semibold transition-colors">
                Giriş Yap
              </Link>
            </p>
          </form>
        </div>
      </div>
    </div>
  );
}
