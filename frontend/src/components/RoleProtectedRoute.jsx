import { Navigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

// ProtectedRoute'un ustune bir kontrol daha ekler: giris yapmis olmak
// yetmez, kullanicinin rolu allowedRoles listesinde olmali. Ornegin
// /inbox'a sadece BUSINESS_OWNER/ADMIN girebilir — USER rolundeki bir
// musteri linki bilse bile (ya da URL'yi elle yazsa bile) buraya
// giremez, ana sayfaya yonlendirilir.
//
// ONEMLI: Bu SADECE bir UI/UX kolayligi — "yanlis yerde olmadigini"
// erkenden gostermek icin. Backend zaten OwnershipGuard/@PreAuthorize
// ile ayni kontrolu kendi tarafinda bagimsiz yapiyor (Faz 0.4); bu
// route koruması olmasa bile API'ler guvenli kalir. Gercek guvenlik
// sinirini asla frontend'e birakma.
export default function RoleProtectedRoute({ children, allowedRoles }) {
  const { isAuthenticated, user } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (!allowedRoles.includes(user?.role)) {
    return <Navigate to="/" replace />;
  }

  return children;
}
