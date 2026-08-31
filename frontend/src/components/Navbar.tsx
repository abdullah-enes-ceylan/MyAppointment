import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useState, useEffect, useRef, type FormEvent } from "react";
import { useAuth } from "../context/AuthContext";
import logoIcon from "../assets/logo-icon.png";

const OWNER_ROLES = ["BUSINESS_OWNER", "ADMIN"];

// Seçili konum etiketi Navbar'da (kompakt) VE HomePage'in Hero'sunda (büyük)
// gösteriliyor -- ikisi kardeş bileşen olduğu için doğrudan state
// paylaşamıyorlar; araya bir context kurmak yerine localStorage + custom
// event kullanıyoruz: HomePage yazıp olayı tetikliyor, Navbar dinleyip
// kendini güncelliyor. Tek bir string için ayrı bir Provider katmanı kurmak
// fazla olurdu.
//
// NOT: Bu köprü bir onceki commit'te (Hero'yu tek sahip yaparak) kaldirilmisti,
// ama "Navbar'da da kompakt konum+arama olsun" karari (Google AI Studio
// prototipiyle 2. karsilastirma) ile GERI GETIRILDI -- artik iki ayri
// bilesen (Navbar'in kompakt gosterimi + Hero'nun buyuk gosterimi) ayni
// etiketi senkron tutmak zorunda.
export const LOCATION_STORAGE_KEY = "randevum_location_label";
export const LOCATION_CHANGED_EVENT = "randevum:location-changed";

export function setLocationLabel(label: string | null) {
  if (label) {
    localStorage.setItem(LOCATION_STORAGE_KEY, label);
  } else {
    localStorage.removeItem(LOCATION_STORAGE_KEY);
  }
  window.dispatchEvent(new Event(LOCATION_CHANGED_EVENT));
}

export default function Navbar() {
  const navigate = useNavigate();
  const location = useLocation();
  // Arama sadece ana sayfada anlamli: HomePage disindaki her sayfa zaten
  // arama sonucu gostermiyor, kutuyu orada tutmak sadece kafa karistirirdi.
  // Konum butonu ise HER sayfada gorunur -- tiklaninca ana sayfaya donup
  // Hero'nun "yakinimdakiler" akisini tetikliyor.
  const isHomePage = location.pathname === "/";
  const [searchParams] = useSearchParams();
  const { isAuthenticated, user, logout } = useAuth();
  // user?.role tipi string | null -- "?? ''" gerekcesi RoleProtectedRoute'daki
  // ile ayni (bkz. o dosya): null hicbir role stringiyle eslesmez, davranis
  // ayni kalir, sadece Array.prototype.includes'in bekledigi tip saglanir.
  const isOwner = OWNER_ROLES.includes(user?.role ?? "");

  const [menuOpen, setMenuOpen] = useState(false);
  const [query, setQuery] = useState(searchParams.get("q") ?? "");
  const [locationLabel, setLabel] = useState(() => localStorage.getItem(LOCATION_STORAGE_KEY));
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const sync = () => setLabel(localStorage.getItem(LOCATION_STORAGE_KEY));
    window.addEventListener(LOCATION_CHANGED_EVENT, sync);
    return () => window.removeEventListener(LOCATION_CHANGED_EVENT, sync);
  }, []);

  // Arama kutusu URL'e yazılan q parametresiyle çalışıyor; kullanıcı geri
  // tuşuna basıp aramadan çıkarsa kutu da temizlensin. Hero'daki arama
  // kutusu da aynı q parametresini okuyup yazıyor -- ikisi URL üzerinden
  // senkron kalıyor.
  useEffect(() => {
    setQuery(searchParams.get("q") ?? "");
  }, [searchParams]);

  // Dropdown dışına tıklayınca kapansın.
  useEffect(() => {
    if (!menuOpen) return;
    function onDocClick(e: MouseEvent) {
      if (menuRef.current && e.target instanceof Node && !menuRef.current.contains(e.target)) {
        setMenuOpen(false);
      }
    }
    document.addEventListener("mousedown", onDocClick);
    return () => document.removeEventListener("mousedown", onDocClick);
  }, [menuOpen]);

  function handleLogout() {
    logout();
    setMenuOpen(false);
    navigate("/");
  }

  // Arama tamamen istemci tarafında (HomePage yüklü listeyi filtreliyor) --
  // backend'de arama ucu yok. Sorgu URL'e yazılıyor ki hem HomePage okuyabilsin
  // hem de arama sonucu paylaşılabilir/yer imine eklenebilir olsun.
  function handleSearch(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    navigate(query.trim() ? `/?q=${encodeURIComponent(query.trim())}` : "/");
  }

  const initial = (user?.email?.[0] ?? "?").toUpperCase();

  return (
    <nav className="sticky top-0 z-40 bg-brand">
      <div className="max-w-7xl mx-auto px-4 sm:px-6">
        {/* Tek satır: logo — konum (kompakt, her sayfada) — arama (kompakt,
            sadece ana sayfada) — Randevularım/Favorilerim — bildirim/profil.
            Google AI Studio prototipiyle 2. karşılaştırma sonrası
            (2026-08-30): konum+arama Navbar'a GERİ eklendi, Hero'daki büyük
            arama/konum kutusu da AYRICA duruyor -- prototipin orijinal
            Header+HeroSection ikilisiyle aynı, bilerek iki kez var (biri
            kompakt/her an erişilebilir, biri Hero'nun görsel odak noktası).
            "Ana Sayfa" linki kaldırıldı -- logo zaten aynı işi görüyor,
            referans görselde de yoktu. */}
        <div className="flex items-center gap-2 sm:gap-3 h-14 sm:h-16">
          <Link to="/" className="flex items-center gap-2 shrink-0">
            <img src={logoIcon} alt="Randevum" className="w-8 h-8 object-contain" />
          </Link>

          <button
            onClick={() => navigate("/?nearby=1")}
            className="hidden sm:flex items-center gap-1.5 text-sm text-white/90 hover:text-white transition-colors cursor-pointer min-w-0 shrink-0"
          >
            <span className="shrink-0">📍</span>
            <span className="truncate max-w-[100px]">{locationLabel ?? "Konum seç"}</span>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" className="shrink-0">
              <path d="M6 9l6 6 6-6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>

          {/* flex-1 sarmalayıcı HER sayfada var (sağ grubu doğru itmesi
              için) -- içeriği (arama kutusu) sadece ana sayfada dolu. */}
          <div className="flex-1 min-w-0">
            {isHomePage && (
              <form onSubmit={handleSearch} className="hidden sm:block max-w-md mx-auto">
                <div className="relative">
                  <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <circle cx="11" cy="11" r="7" />
                      <path d="M20 20l-3.5-3.5" strokeLinecap="round" />
                    </svg>
                  </span>
                  <input
                    type="search"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="İşletme, kuaför veya hizmet ara..."
                    className="w-full pl-9 pr-3 py-2 rounded-xl bg-white/10 border border-transparent focus:border-white/30 focus:bg-white text-sm text-white focus:text-slate-900 placeholder-white/50 focus:placeholder-slate-400 transition-all outline-none"
                  />
                </div>
              </form>
            )}
          </div>

          {isAuthenticated && (
            <div className="hidden sm:flex items-center gap-1 shrink-0">
              <Link
                to="/appointments"
                className="px-3 py-2 text-sm font-medium text-white/70 hover:text-white transition-colors whitespace-nowrap"
              >
                Randevularım
              </Link>
              <Link
                to="/favorites"
                className="px-3 py-2 text-sm font-medium text-white/70 hover:text-white transition-colors whitespace-nowrap"
              >
                Favorilerim
              </Link>
            </div>
          )}

          <div className="flex items-center gap-2 sm:gap-3 shrink-0">
            {isAuthenticated ? (
              <>
                {/* Bildirimler: Faz 3.4'teki NotificationPort altyapısı kurulana
                    kadar işlevsiz. Kırmızı nokta da BİLEREK yok -- okunmamış
                    bildirim varmış gibi göstermek yanıltıcı olurdu. */}
                <span
                  title="Bildirimler yakında geliyor"
                  className="text-white/40 cursor-not-allowed select-none"
                >
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
                    <path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" strokeLinecap="round" strokeLinejoin="round" />
                    <path d="M13.7 21a2 2 0 0 1-3.4 0" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                </span>

                <div className="relative" ref={menuRef}>
                  <button
                    onClick={() => setMenuOpen((o) => !o)}
                    className="flex items-center gap-1 cursor-pointer"
                  >
                    <span className="w-8 h-8 rounded-full bg-white/15 text-white text-sm font-semibold flex items-center justify-center">
                      {initial}
                    </span>
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" className="text-white/70">
                      <path d="M6 9l6 6 6-6" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                  </button>

                  {menuOpen && (
                    <div className="absolute right-0 mt-2 w-52 bg-white rounded-xl shadow-xl border border-slate-200 py-1.5 overflow-hidden">
                      <div className="px-3 py-2 border-b border-slate-100">
                        <p className="text-xs text-slate-500 truncate">{user?.email}</p>
                      </div>
                      {[
                        { to: "/profile", label: "👤 Profil" },
                        ...(isOwner ? [{ to: "/panel", label: "🏢 İşletme Paneli" }] : []),
                      ].map((item) => (
                        <Link
                          key={item.to}
                          to={item.to}
                          onClick={() => setMenuOpen(false)}
                          className="block px-3 py-2 text-sm text-slate-700 hover:bg-slate-50 transition-colors"
                        >
                          {item.label}
                        </Link>
                      ))}
                      <button
                        onClick={handleLogout}
                        className="w-full text-left px-3 py-2 text-sm text-red-600 hover:bg-red-50 transition-colors cursor-pointer border-t border-slate-100 mt-1 pt-2 cursor-pointer"
                      >
                        Çıkış Yap
                      </button>
                    </div>
                  )}
                </div>
              </>
            ) : (
              <>
                <Link
                  to="/login"
                  className="px-3 py-2 text-sm font-medium text-white/85 hover:text-white transition-colors"
                >
                  Giriş Yap
                </Link>
                <Link
                  to="/register"
                  className="px-3.5 py-2 text-sm font-medium text-brand bg-white hover:bg-slate-100 rounded-xl transition-colors"
                >
                  Kayıt Ol
                </Link>
              </>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}
