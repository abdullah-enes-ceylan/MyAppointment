import { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import api from "../api/axios";
import Toast from "../components/Toast";
import { useAuth } from "../context/AuthContext";

const ROLE_LABELS = {
  USER: "Müşteri",
  BUSINESS_OWNER: "İşletme Sahibi",
  ADMIN: "Yönetici",
};

const OWNER_ROLES = ["BUSINESS_OWNER", "ADMIN"];

// Backend'in ValidationErrorResponse'u alan bazlı hata haritası döner
// (bkz. GlobalExceptionHandler.handleValidation) -- onu formun altına
// alan alan basabilmek için ayıklıyoruz. Alan bazlı hata yoksa (ör.
// BusinessRuleException) tek bir genel mesaj kalır.
function extractErrors(err) {
  const data = err.response?.data;
  return {
    fieldErrors: data?.fieldErrors ?? {},
    message: data?.message || "Beklenmeyen bir hata oluştu.",
  };
}

function StatTile({ icon, label, value }) {
  return (
    <div className="bg-white border border-slate-200 rounded-2xl px-4 py-3.5">
      <div className="text-xl">{icon}</div>
      <div className="text-2xl font-bold text-slate-900 mt-1">{value}</div>
      <div className="text-xs text-slate-500 mt-0.5">{label}</div>
    </div>
  );
}

function Field({ id, label, error, ...inputProps }) {
  return (
    <div>
      <label htmlFor={id} className="block text-xs font-medium text-slate-500 mb-1.5">
        {label}
      </label>
      <input
        id={id}
        {...inputProps}
        className={`w-full px-4 py-2.5 bg-white border rounded-xl text-slate-900 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 transition-all disabled:bg-slate-50 disabled:text-slate-400 ${
          error
            ? "border-red-300 focus:ring-red-200"
            : "border-slate-200 focus:ring-[#161b33]/20 focus:border-[#161b33]/40"
        }`}
      />
      {error && <p className="mt-1 text-xs text-red-500">{error}</p>}
    </div>
  );
}

export default function ProfilePage() {
  const { user } = useAuth();
  const isOwner = OWNER_ROLES.includes(user?.role);

  const [profile, setProfile] = useState(null);
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState(null);

  const [infoForm, setInfoForm] = useState({ name: "", surName: "", phone: "" });
  const [infoErrors, setInfoErrors] = useState({});
  const [infoSaving, setInfoSaving] = useState(false);

  const [pwForm, setPwForm] = useState({ currentPassword: "", newPassword: "", confirmPassword: "" });
  const [pwErrors, setPwErrors] = useState({});
  const [pwSaving, setPwSaving] = useState(false);

  useEffect(() => {
    async function fetchProfile() {
      try {
        const res = await api.get("/api/users/me");
        setProfile(res.data);
        setInfoForm({ name: res.data.name, surName: res.data.surName, phone: res.data.phone });
      } catch {
        setToast({ message: "Profil bilgileri yüklenemedi.", type: "error" });
      } finally {
        setLoading(false);
      }
    }
    fetchProfile();
  }, []);

  // Sayılar ayrı uçtan -- profil bilgisinin kendisinden bağımsız, biri
  // yüklenemezse diğeri yine de görünsün (bkz. ProfileStatsResponse).
  useEffect(() => {
    api
      .get("/api/users/me/stats")
      .then((res) => setStats(res.data))
      .catch(() => {
        // Sessizce yut -- sayılar sayfanın ana işlevi (bilgi düzenleme)
        // için kritik değil, ayrı bir hata toast'ı gereksiz gürültü olurdu.
      });
  }, []);

  async function handleInfoSubmit(e) {
    e.preventDefault();
    setInfoSaving(true);
    setInfoErrors({});

    try {
      const res = await api.put("/api/users/me", infoForm);
      setProfile(res.data);
      setToast({ message: "Profil bilgileriniz güncellendi.", type: "success" });
    } catch (err) {
      const { fieldErrors, message } = extractErrors(err);
      setInfoErrors(fieldErrors);
      if (Object.keys(fieldErrors).length === 0) {
        setToast({ message, type: "error" });
      }
    } finally {
      setInfoSaving(false);
    }
  }

  async function handlePasswordSubmit(e) {
    e.preventDefault();
    setPwErrors({});

    // "Yeni şifre tekrar" SADECE frontend'de var -- backend'e gönderilmiyor,
    // çünkü bu bir iş kuralı değil, kullanıcının yazım hatasına karşı bir
    // arayüz önlemi. Backend'in doğrulaması gereken şey yeni şifrenin
    // kendisi (uzunluk) ve mevcut şifrenin doğruluğu.
    if (pwForm.newPassword !== pwForm.confirmPassword) {
      setPwErrors({ confirmPassword: "Şifreler eşleşmiyor." });
      return;
    }

    setPwSaving(true);
    try {
      await api.put("/api/users/me/password", {
        currentPassword: pwForm.currentPassword,
        newPassword: pwForm.newPassword,
      });
      setPwForm({ currentPassword: "", newPassword: "", confirmPassword: "" });
      setToast({ message: "Şifreniz değiştirildi.", type: "success" });
    } catch (err) {
      const { fieldErrors, message } = extractErrors(err);
      setPwErrors(fieldErrors);
      if (Object.keys(fieldErrors).length === 0) {
        setToast({ message, type: "error" });
      }
    } finally {
      setPwSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="bg-slate-50 min-h-[calc(100vh-4rem)] flex justify-center items-start pt-24">
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

  const initials = profile ? `${profile.name?.[0] ?? ""}${profile.surName?.[0] ?? ""}`.toUpperCase() : "";

  return (
    <div className="bg-slate-50 min-h-[calc(100vh-4rem)]">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8">
        {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

        {/* Kimlik başlığı */}
        <div className="flex items-center gap-4 mb-6">
          <div className="w-16 h-16 shrink-0 rounded-2xl bg-[#161b33] text-white flex items-center justify-center text-xl font-bold">
            {initials}
          </div>
          <div className="min-w-0">
            <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 truncate">
              {profile?.name} {profile?.surName}
            </h1>
            <div className="flex flex-wrap items-center gap-2 mt-1">
              <span className="text-sm text-slate-500">{profile?.email}</span>
              <span className="text-xs font-medium text-blue-700 bg-blue-50 border border-blue-200 px-2 py-0.5 rounded-lg">
                {ROLE_LABELS[profile?.role] ?? profile?.role}
              </span>
            </div>
          </div>
        </div>

        {/* Özet sayılar — hepsi gerçek veriden (GET /api/users/me/stats) */}
        {stats && (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3 mb-6">
            <StatTile icon="📅" label="Toplam randevu" value={stats.totalAppointments} />
            <StatTile icon="⏳" label="Yaklaşan randevu" value={stats.upcomingAppointments} />
            <StatTile icon="✅" label="Tamamlanan" value={stats.completedAppointments} />
            <StatTile icon="❤️" label="Favori işletme" value={stats.favoriteCount} />
            <StatTile icon="⭐" label="Yaptığım yorum" value={stats.reviewCount} />
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          {/* Profil bilgileri */}
          <div className="bg-white border border-slate-200 shadow-sm rounded-2xl p-5 sm:p-6">
            <h2 className="text-lg font-semibold text-slate-900 mb-1">Profil Bilgileri</h2>
            <p className="text-sm text-slate-500 mb-5">Ad, soyad ve telefon bilgilerinizi güncelleyebilirsiniz.</p>

            <form onSubmit={handleInfoSubmit} className="space-y-4">
              <Field
                id="name"
                label="Ad"
                value={infoForm.name}
                onChange={(e) => setInfoForm({ ...infoForm, name: e.target.value })}
                error={infoErrors.name}
              />
              <Field
                id="surName"
                label="Soyad"
                value={infoForm.surName}
                onChange={(e) => setInfoForm({ ...infoForm, surName: e.target.value })}
                error={infoErrors.surName}
              />
              <Field
                id="phone"
                label="Telefon"
                value={infoForm.phone}
                onChange={(e) => setInfoForm({ ...infoForm, phone: e.target.value })}
                error={infoErrors.phone}
              />

              {/* E-posta salt okunur: hem giriş kimliği hem unique kısıt.
                  Değiştirilebilir yapmak doğrulama e-postası akışı gerektirir
                  (bkz. UpdateProfileRequest'teki açıklama). */}
              <Field
                id="email"
                label="E-posta (değiştirilemez)"
                value={profile?.email ?? ""}
                disabled
                readOnly
              />

              <button
                type="submit"
                disabled={infoSaving}
                className="w-full py-2.5 text-sm font-semibold text-white bg-[#161b33] hover:bg-[#20264a] rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-200 cursor-pointer"
              >
                {infoSaving ? "Kaydediliyor..." : "Değişiklikleri Kaydet"}
              </button>
            </form>
          </div>

          {/* Şifre değiştirme */}
          <div className="bg-white border border-slate-200 shadow-sm rounded-2xl p-5 sm:p-6">
            <h2 className="text-lg font-semibold text-slate-900 mb-1">Şifre Değiştir</h2>
            <p className="text-sm text-slate-500 mb-5">
              Güvenliğiniz için önce mevcut şifrenizi doğrulamanız gerekiyor.
            </p>

            <form onSubmit={handlePasswordSubmit} className="space-y-4">
              <Field
                id="currentPassword"
                label="Mevcut şifre"
                type="password"
                placeholder="••••••••"
                value={pwForm.currentPassword}
                onChange={(e) => setPwForm({ ...pwForm, currentPassword: e.target.value })}
                error={pwErrors.currentPassword}
              />
              <Field
                id="newPassword"
                label="Yeni şifre (en az 8 karakter)"
                type="password"
                placeholder="••••••••"
                value={pwForm.newPassword}
                onChange={(e) => setPwForm({ ...pwForm, newPassword: e.target.value })}
                error={pwErrors.newPassword}
              />
              <Field
                id="confirmPassword"
                label="Yeni şifre (tekrar)"
                type="password"
                placeholder="••••••••"
                value={pwForm.confirmPassword}
                onChange={(e) => setPwForm({ ...pwForm, confirmPassword: e.target.value })}
                error={pwErrors.confirmPassword}
              />

              <button
                type="submit"
                disabled={pwSaving}
                className="w-full py-2.5 text-sm font-semibold text-white bg-[#161b33] hover:bg-[#20264a] rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-200 cursor-pointer"
              >
                {pwSaving ? "Değiştiriliyor..." : "Şifreyi Değiştir"}
              </button>
            </form>
          </div>
        </div>

        {/* Hızlı erişim */}
        <div className="bg-white border border-slate-200 shadow-sm rounded-2xl p-5 sm:p-6 mt-5">
          <h2 className="text-lg font-semibold text-slate-900 mb-4">Hızlı Erişim</h2>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <Link
              to="/appointments"
              className="flex items-center gap-3 px-4 py-3 bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-xl transition-colors"
            >
              <span className="text-xl">📅</span>
              <span className="text-sm font-medium text-slate-700">Randevularım</span>
            </Link>
            <Link
              to="/favorites"
              className="flex items-center gap-3 px-4 py-3 bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-xl transition-colors"
            >
              <span className="text-xl">❤️</span>
              <span className="text-sm font-medium text-slate-700">Favorilerim</span>
            </Link>
            {isOwner && (
              <Link
                to="/panel"
                className="flex items-center gap-3 px-4 py-3 bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-xl transition-colors"
              >
                <span className="text-xl">🏢</span>
                <span className="text-sm font-medium text-slate-700">İşletme Panelim</span>
              </Link>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
