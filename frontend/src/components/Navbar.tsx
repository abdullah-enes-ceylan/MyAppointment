import { Link, NavLink, useNavigate } from "react-router-dom";
import { useState, useEffect, useRef } from "react";
import { useAuth } from "../context/AuthContext";
import logoIcon from "../assets/logo-icon.png";

const OWNER_ROLES = ["BUSINESS_OWNER", "ADMIN"];

export default function Navbar() {
  const navigate = useNavigate();
  const { isAuthenticated, user, logout } = useAuth();
  // user?.role tipi string | null -- "?? ''" gerekcesi RoleProtectedRoute'daki
  // ile ayni (bkz. o dosya): null hicbir role stringiyle eslesmez, davranis
  // ayni kalir, sadece Array.prototype.includes'in bekledigi tip saglanir.
  const isOwner = OWNER_ROLES.includes(user?.role ?? "");

  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

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

  return (
    <nav className="sticky top-0 z-40 bg-brand">
      <div className="max-w-7xl mx-auto px-4 sm:px-6">
        {/* Tek satır: logo (sol) — hızlı erişim linkleri (şeridin TAM
            ORTASI) — bildirim/profil (sağ). Konum seçme + arama artık
            burada DEĞİL -- Hero section'a taşındı (bkz. HomePage.tsx),
            çünkü sadece ana sayfada anlamlıydı ve orada zaten HomePage'in
            kendi state'ine (searchQuery, locationLabel) doğrudan erişimi
            var; ayrı bileşenler (Navbar/HomePage) arasında localStorage +
            custom event köprüsü kurmaya gerek kalmadı.
            Isletme kategori sekmeleri de BURADA DEGIL -- HomePage'in kendi
            govdesinde, cinsiyet barinin ustunde ayri bir serit (bkz.
            HomePage.tsx).
            Grid ile grid-cols-[1fr_auto_1fr] KASITLI: basit bir flex +
            justify-between kullansaydik orta grup, sol (logo) ve sag
            (bildirim+avatar) gruplarinin GENISLIKLERI FARKLI oldugu icin
            gercek merkezde degil, daha genis olan tarafa dogru kaymis
            dururdu. Iki disi sutunu ESIT (1fr/1fr) yaparak orta sutunun
            konumunu sol/sag icerigin genisliginden tamamen BAGIMSIZ hale
            getiriyoruz -- ortadaki grup, disindaki icerik ne olursa olsun
            hep tam merkezde kalir. */}
        <div className="grid grid-cols-[1fr_auto_1fr] items-center h-14 sm:h-16">
          <Link to="/" className="flex items-center gap-2 shrink-0 justify-self-start">
            <img src={logoIcon} alt="Randevum" className="w-8 h-8 object-contain" />
          </Link>

          {/* Ana Sayfa / Randevularım / Favorilerim -- eskiden avatar
              dropdown'unun icindeydi, hizli erisim icin seridin ortasina
              tasindi. Ayni kosul: sadece giris yapmis kullanicida
              (dropdown'daki eski kosulun birebir aynisi). Mobilde yer yok
              -- BottomTabBar zaten ayni uc hedefi (Kesfet/Randevularim/
              Favorilerim) tasidigi icin burada tekrar etmeye gerek yok.
              ONEMLI: mobil gizleme sarmalayici DIV'e "hidden" (display:none)
              ile DEGIL, tek tek linklere uygulanmis "hidden sm:inline-block"
              ile yapiliyor. display:none olan bir grid ogesi CSS Grid'in
              otomatik yerlesiminden TAMAMEN cikiyor -- bu da sag gruptaki
              bildirim/avatar'in 3. sutun yerine bosalan 2. sutuna kayip
              artik sag-hizali durmamasina yol aciyordu (canli testte
              gozlemlendi). Sarmalayici HER ZAMAN flex kalarak orta sutunu
              yapisal olarak korur, mobilde ise icindeki linkler gorunmez +
              genisligi sifira duser, ayni gorsel sonucu verir. */}
          <div className="flex items-center gap-1 justify-self-center">
            {isAuthenticated &&
              [
                { to: "/", label: "Ana Sayfa", end: true },
                { to: "/appointments", label: "Randevularım", end: false },
                { to: "/favorites", label: "Favorilerim", end: false },
              ].map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    `hidden sm:inline-block px-3 py-2 text-sm font-medium transition-colors whitespace-nowrap ${
                      isActive ? "text-white" : "text-white/70 hover:text-white"
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              ))}
          </div>

          <div className="flex items-center gap-2 sm:gap-3 shrink-0 justify-self-end">
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
