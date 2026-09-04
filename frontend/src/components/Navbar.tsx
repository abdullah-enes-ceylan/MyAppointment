import { Link, useLocation, useNavigate } from "react-router-dom";
import { useState, useEffect, useRef } from "react";
import { Bell, Calendar, ChevronDown, Heart, MapPin } from "lucide-react";
import { useAuth } from "../context/AuthContext";
import api from "../api/axios";
import type { ProfileStatsResponse } from "../types/api";
import { RandevumLogo } from "./RandevumLogo";

const OWNER_ROLES = ["BUSINESS_OWNER", "ADMIN"];

// Seçili konum etiketi Navbar'da (kompakt) VE HomePage'in Hero'sunda (büyük)
// gösteriliyor -- ikisi kardeş bileşen olduğu için doğrudan state
// paylaşamıyorlar; araya bir context kurmak yerine localStorage + custom
// event kullanıyoruz: HomePage yazıp olayı tetikliyor, Navbar dinleyip
// kendini güncelliyor. Tek bir string için ayrı bir Provider katmanı kurmak
// fazla olurdu.
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

// 2. Google AI Studio prototipiyle karşılaştırma sonrası (2026-09-04):
// koyu lacivert dolgu navbar yerine BEYAZ/açık navbar, "Keşfet" sekmesi,
// Randevularım/Favorilerim yanında GERÇEK sayı rozetleri (ProfileStatsResponse
// üzerinden). AI Studio'daki semt dropdown'ı (Nişantaşı/Kadıköy/... arasında
// seçim) BİLEREK taşınmadı -- bizim Business modelimizde ayrı bir "district"
// alanı yok, sahte bir dropdown gerçek hiçbir şeyi filtrelemezdi. Bunun
// yerine mevcut "Konum seç" davranışı (tıklayınca /?nearby=1'e gidip gerçek
// konum bazlı yakınımdakiler akışını tetikliyor) aynı kalıp sadece yeni
// görünüme uyarlandı.
export default function Navbar() {
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated, user, logout } = useAuth();
  // user?.role tipi string | null -- "?? ''" gerekcesi RoleProtectedRoute'daki
  // ile ayni (bkz. o dosya): null hicbir role stringiyle eslesmez, davranis
  // ayni kalir, sadece Array.prototype.includes'in bekledigi tip saglanir.
  const isOwner = OWNER_ROLES.includes(user?.role ?? "");
  const isHomePage = location.pathname === "/";

  const [menuOpen, setMenuOpen] = useState(false);
  const [locationLabel, setLabel] = useState(() => localStorage.getItem(LOCATION_STORAGE_KEY));
  const [stats, setStats] = useState<ProfileStatsResponse | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const sync = () => setLabel(localStorage.getItem(LOCATION_STORAGE_KEY));
    window.addEventListener(LOCATION_CHANGED_EVENT, sync);
    return () => window.removeEventListener(LOCATION_CHANGED_EVENT, sync);
  }, []);

  // Randevularım/Favorilerim rozetleri gerçek sayı -- sahte/sabit değer değil.
  useEffect(() => {
    if (!isAuthenticated) {
      setStats(null);
      return;
    }
    let cancelled = false;
    api
      .get<ProfileStatsResponse>("/api/users/me/stats")
      .then((res) => {
        if (!cancelled) setStats(res.data);
      })
      .catch(() => {
        if (!cancelled) setStats(null);
      });
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, location.pathname]);

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

  const initial = (user?.email?.[0] ?? "?").toUpperCase();
  const isAppointmentsPage = location.pathname === "/appointments";
  const isFavoritesPage = location.pathname === "/favorites";
  // Randevu alma sayfasında konum seçmenin bir anlamı yok (kullanıcı zaten
  // belirli bir işletmeye bakıyor) -- kullanıcı isteği üzerine bu sayfada
  // gizlendi.
  const isBusinessDetailPage = location.pathname.startsWith("/business/");

  return (
    <header className="sticky top-0 z-40 bg-white/95 backdrop-blur-md border-b border-slate-200/80 shadow-[0_1px_3px_rgba(15,23,42,0.03)]">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 sm:h-18 flex items-center justify-between gap-4">
        {/* Sol: logo, Keşfet sekmesi, konum */}
        <div className="flex items-center gap-3 sm:gap-4 min-w-0">
          <Link to="/" className="flex items-center shrink-0 group" aria-label="Ana Sayfa">
            <RandevumLogo className="w-9 h-9 sm:w-10 sm:h-10 transition-transform duration-200 group-hover:scale-105" />
          </Link>

          <nav className="hidden sm:flex items-center gap-1.5 pl-2 sm:pl-3 border-l border-slate-200/80 shrink-0">
            <Link
              to="/"
              className={`px-3 py-1.5 rounded-xl text-sm font-semibold transition ${
                isHomePage ? "bg-slate-100 text-slate-900" : "text-slate-600 hover:text-slate-900 hover:bg-slate-50"
              }`}
            >
              Keşfet
            </Link>
          </nav>

          {!isBusinessDetailPage && (
            <button
              type="button"
              onClick={() => navigate("/?nearby=1")}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs sm:text-sm font-medium text-slate-700 bg-slate-100/90 hover:bg-slate-200/70 transition border border-slate-200/80 min-w-0 shrink-0"
            >
              <MapPin className="w-3.5 h-3.5 text-brand shrink-0" />
              <span className="font-semibold text-slate-900 truncate max-w-[100px] sm:max-w-none">
                {locationLabel ?? "Konum seç"}
              </span>
              <ChevronDown className="w-3.5 h-3.5 text-slate-400 shrink-0" />
            </button>
          )}
        </div>

        {/* Sağ: bildirim/favoriler/randevular/profil */}
        <div className="flex items-center gap-1.5 sm:gap-2.5 shrink-0">
          {isAuthenticated && (
            <>
              <Link
                to="/favorites"
                className={`relative flex items-center gap-2 px-3 sm:px-3.5 py-2 rounded-xl text-xs sm:text-sm font-semibold transition ${
                  isFavoritesPage
                    ? "bg-[#0b1b2d] text-white border border-rose-400/30"
                    : "border border-slate-200/80 hover:bg-slate-100 text-slate-800"
                }`}
              >
                <Heart className={`w-4 h-4 ${isFavoritesPage ? "fill-rose-400 text-rose-400" : "text-slate-600"}`} />
                <span className="hidden md:inline">Favorilerim</span>
                {!!stats?.favoriteCount && (
                  <span
                    className={`w-5 h-5 rounded-full text-[10px] font-bold flex items-center justify-center ${
                      isFavoritesPage ? "bg-rose-500 text-white" : "bg-rose-100 text-rose-700"
                    }`}
                  >
                    {stats.favoriteCount}
                  </span>
                )}
              </Link>

              <Link
                to="/appointments"
                className={`relative flex items-center gap-2 px-3 sm:px-3.5 py-2 rounded-xl text-xs sm:text-sm font-semibold transition ${
                  isAppointmentsPage
                    ? "bg-[#0b1b2d] text-white border border-sky-400/30"
                    : "border border-slate-200/80 hover:bg-slate-100 text-slate-800"
                }`}
              >
                <Calendar className={`w-4 h-4 ${isAppointmentsPage ? "text-sky-400" : "text-brand"}`} />
                <span className="hidden md:inline">Randevularım</span>
                {!!stats?.totalAppointments && (
                  <span
                    className={`w-5 h-5 rounded-full text-[10px] font-bold flex items-center justify-center ${
                      isAppointmentsPage ? "bg-sky-500 text-white" : "bg-brand text-white"
                    }`}
                  >
                    {stats.totalAppointments}
                  </span>
                )}
              </Link>

              {/* Bildirimler: Faz 3.4'teki NotificationPort altyapısı kurulana
                  kadar işlevsiz. Kırmızı nokta da BİLEREK yok -- okunmamış
                  bildirim varmış gibi göstermek yanıltıcı olurdu. */}
              <span
                title="Bildirimler yakında geliyor"
                className="hidden sm:flex p-2 rounded-xl text-slate-400 cursor-not-allowed select-none"
              >
                <Bell className="w-4 h-4" />
              </span>

              <div className="relative pl-1 sm:pl-2 border-l border-slate-200" ref={menuRef}>
                <button
                  onClick={() => setMenuOpen((o) => !o)}
                  className="flex items-center gap-1 cursor-pointer"
                >
                  <span className="w-9 h-9 rounded-full bg-brand text-white text-xs font-semibold flex items-center justify-center">
                    {initial}
                  </span>
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
                      className="w-full text-left px-3 py-2 text-sm text-red-600 hover:bg-red-50 transition-colors cursor-pointer border-t border-slate-100 mt-1 pt-2"
                    >
                      Çıkış Yap
                    </button>
                  </div>
                )}
              </div>
            </>
          )}

          {!isAuthenticated && (
            <>
              <Link
                to="/login"
                className="px-3 py-2 text-sm font-medium text-slate-700 hover:text-slate-900 transition-colors"
              >
                Giriş Yap
              </Link>
              <Link
                to="/register"
                className="px-3.5 py-2 text-sm font-medium text-white bg-brand hover:bg-brand-hover rounded-xl transition-colors"
              >
                Kayıt Ol
              </Link>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
