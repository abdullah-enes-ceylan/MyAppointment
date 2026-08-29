import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

// Bu SADECE bir UI/UX kolayligi — "yanlis yerde olmadigini" erkenden
// gostermek icin. Backend zaten OwnershipGuard/@PreAuthorize ile ayni
// kontrolu kendi tarafinda bagimsiz yapiyor (Faz 0.4); bu route korumasi
// olmasa bile API'ler guvenli kalir. Gercek yetki siniri asla frontend'e
// birakilmaz.
interface RoleProtectedRouteProps {
  children: ReactNode;
  allowedRoles: string[];
}

// Ustune bir kontrol daha ekler: giris yapmis olmak yetmez, kullanicinin
// rolu allowedRoles listesinde olmali. Ornegin /inbox'a sadece
// BUSINESS_OWNER/ADMIN girebilir.
//
// user?.role'un tipi string | null (bkz. AuthContext.AuthUser) --
// Array.prototype.includes bir string bekliyor, null kabul etmiyor.
// "?? ''" ile null hicbir gercek role degeriyle eslesmeyen bir degere
// donusuyor -- davranis aynen koruniyor (null zaten hicbir zaman
// allowedRoles icinde olamazdi), sadece tip dogru ifade ediliyor.
export default function RoleProtectedRoute({ children, allowedRoles }: RoleProtectedRouteProps) {
  const { isAuthenticated, user } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (!allowedRoles.includes(user?.role ?? "")) {
    return <Navigate to="/" replace />;
  }

  return children;
}
