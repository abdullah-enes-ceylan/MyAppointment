import type { SVGProps } from "react";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthContext";
import Navbar from "./components/Navbar";
import BottomTabBar from "./components/BottomTabBar";
import ProtectedRoute from "./components/ProtectedRoute";
import RoleProtectedRoute from "./components/RoleProtectedRoute";
import HomePage from "./pages/HomePage";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import BusinessDetailPage from "./pages/BusinessDetailPage";
import BusinessPanelPage from "./pages/panel/BusinessPanelPage";
import MyAppointmentsPage from "./pages/MyAppointmentsPage";
import FavoritesPage from "./pages/FavoritesPage";
import ProfilePage from "./pages/ProfilePage";

const FOOTER_LINKS = ["Hakkımızda", "Destek", "Kullanım Koşulları", "Gizlilik"];

const socialIcon: SVGProps<SVGSVGElement> = {
  width: 18,
  height: 18,
  viewBox: "0 0 24 24",
  fill: "currentColor",
};

// AuthProvider'ın İÇİNDE olmak zorunda (useAuth kullanıyor), o yüzden
// ayrı bir bileşen -- App'in kendisi Provider'ı kuran taraf.
function Layout() {
  const { isAuthenticated } = useAuth();

  return (
    <div className="min-h-screen bg-slate-50 font-sans flex flex-col">
      <Navbar />

      <main className="flex-1">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            path="/business/:id"
            element={
              <ProtectedRoute>
                <BusinessDetailPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/panel"
            element={
              <RoleProtectedRoute allowedRoles={["BUSINESS_OWNER", "ADMIN"]}>
                <BusinessPanelPage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/appointments"
            element={
              <ProtectedRoute>
                <MyAppointmentsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/favorites"
            element={
              <ProtectedRoute>
                <FavoritesPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/profile"
            element={
              <ProtectedRoute>
                <ProfilePage />
              </ProtectedRoute>
            }
          />
        </Routes>
      </main>

      {/* Footer mobilde gizli: alt sekme çubuğu zaten ekranın altını
          kaplıyor, ikisi üst üste binerdi. */}
      <footer className={`bg-[#161b33] ${isAuthenticated ? "hidden sm:block" : ""}`}>
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-5 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="flex flex-wrap items-center justify-center gap-x-5 gap-y-2">
            {/* Bu sayfalar henüz yok -- KVKK/hukuki metinler Faz 3.9'da
                yazılacak (bkz. ROADMAP). Link vermek 404'e götürürdü. */}
            {FOOTER_LINKS.map((label) => (
              <span
                key={label}
                title="Bu sayfa yakında eklenecek"
                className="text-xs text-white/60 cursor-not-allowed select-none"
              >
                {label}
              </span>
            ))}
          </div>

          <div className="flex items-center gap-4 text-white/60">
            <span title="X (Twitter) — hesap henüz yok" className="cursor-not-allowed">
              <svg {...socialIcon}>
                <path d="M18.9 2H22l-7 8 8.2 12h-6.4l-5-7.3L5.9 22H2.8l7.5-8.6L2.4 2h6.6l4.5 6.7zm-1.1 18h1.7L7.3 3.8H5.5z" />
              </svg>
            </span>
            <span title="Facebook — hesap henüz yok" className="cursor-not-allowed">
              <svg {...socialIcon}>
                <path d="M22 12a10 10 0 1 0-11.6 9.9v-7H7.9V12h2.5V9.8c0-2.5 1.5-3.9 3.8-3.9 1.1 0 2.2.2 2.2.2v2.5h-1.3c-1.2 0-1.6.8-1.6 1.6V12h2.8l-.4 2.9h-2.4v7A10 10 0 0 0 22 12z" />
              </svg>
            </span>
            <span title="Instagram — hesap henüz yok" className="cursor-not-allowed">
              <svg {...socialIcon}>
                <path d="M12 2.2c3.2 0 3.6 0 4.9.1 1.2.1 1.8.2 2.2.4.6.2 1 .5 1.4.9.4.4.7.8.9 1.4.2.4.4 1 .4 2.2.1 1.3.1 1.7.1 4.9s0 3.6-.1 4.9c-.1 1.2-.2 1.8-.4 2.2-.2.6-.5 1-.9 1.4-.4.4-.8.7-1.4.9-.4.2-1 .4-2.2.4-1.3.1-1.7.1-4.9.1s-3.6 0-4.9-.1c-1.2-.1-1.8-.2-2.2-.4-.6-.2-1-.5-1.4-.9-.4-.4-.7-.8-.9-1.4-.2-.4-.4-1-.4-2.2C2.2 15.6 2.2 15.2 2.2 12s0-3.6.1-4.9c.1-1.2.2-1.8.4-2.2.2-.6.5-1 .9-1.4.4-.4.8-.7 1.4-.9.4-.2 1-.4 2.2-.4C8.4 2.2 8.8 2.2 12 2.2zm0 3.2A6.6 6.6 0 1 0 18.6 12 6.6 6.6 0 0 0 12 5.4zm0 10.9A4.3 4.3 0 1 1 16.3 12 4.3 4.3 0 0 1 12 16.3zm6.9-11.1a1.5 1.5 0 1 1-1.5-1.5 1.5 1.5 0 0 1 1.5 1.5z" />
              </svg>
            </span>
          </div>
        </div>
      </footer>

      {isAuthenticated && (
        <>
          <BottomTabBar />
          {/* Alt çubuğun içeriği örtmemesi için mobilde boşluk */}
          <div className="sm:hidden h-16" />
        </>
      )}
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Layout />
      </BrowserRouter>
    </AuthProvider>
  );
}
