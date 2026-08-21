import { createContext, useContext, useState, useCallback } from "react";
import { jwtDecode } from "jwt-decode";

// Uygulamanin her yerinden "kim giris yapmis, rolu ne" bilgisine tek
// yerden erisim saglar. Eskiden Navbar dogrudan localStorage.getItem
// ("token") okuyordu ve login/logout sonrasi TUM sayfayi yeniden
// yuklemek (window.location.reload()) zorundaydi -- cunku React'in
// state'i localStorage degisikliginden haberdar olmuyordu. AuthContext
// ile login/logout artik normal React state guncellemesi, sayfa hic
// yenilenmeden her bilesen otomatik yeniden render oluyor.
const AuthContext = createContext(null);

// JWT'nin role claim'i backend'de SADECE token uretilirken yazilir,
// yetkilendirme icin HIC KULLANILMAZ (backend her istekte rolu
// veritabanindan taze okur — bkz. CustomUserDetailsService, Faz 0.4).
// Burada rolu decode etmek SADECE arayuzde "hangi linkleri gosterecegim"
// karari icin — bir GUVENLIK SINIRI DEGIL. Gercek yetki kontrolu her
// zaman backend'de. Rol backend'de degisirse (ornegin isletme sahibi
// olma), kullanici yeniden giris yapana kadar token'daki eski rolu
// tasir — bu, salt UI gorunumu icin kabul edilebilir bir gecikme.
function decodeUser(token) {
  if (!token) return null;
  try {
    const payload = jwtDecode(token);
    // Backend "ROLE_USER" gibi onekli yaziyor (bkz. CustomUserDetailsService).
    const role = payload.role?.replace(/^ROLE_/, "") ?? null;
    return { email: payload.sub, role };
  } catch {
    // Bozuk/cozulemeyen token — giris yapilmamis gibi davran.
    return null;
  }
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem("token"));
  const [user, setUser] = useState(() => decodeUser(localStorage.getItem("token")));

  const login = useCallback((newToken) => {
    localStorage.setItem("token", newToken);
    setToken(newToken);
    setUser(decodeUser(newToken));
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem("token");
    setToken(null);
    setUser(null);
  }, []);

  const value = {
    token,
    user,
    isAuthenticated: Boolean(token),
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth, AuthProvider icinde kullanilmali.");
  }
  return context;
}
