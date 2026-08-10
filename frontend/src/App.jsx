import { BrowserRouter, Routes, Route } from "react-router-dom";
import Navbar from "./components/Navbar";
import ProtectedRoute from "./components/ProtectedRoute";
import HomePage from "./pages/HomePage";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import BusinessDetailPage from "./pages/BusinessDetailPage";
import PendingAppointments from "./pages/PendingAppointments";

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-bg font-sans">
        {/* Animated Background Blobs */}
        <div className="fixed inset-0 overflow-hidden pointer-events-none">
          <div className="absolute -top-40 -left-40 w-80 h-80 bg-emerald-500/8 rounded-full blur-3xl animate-pulse" />
          <div className="absolute -bottom-40 -right-40 w-96 h-96 bg-teal-500/8 rounded-full blur-3xl animate-pulse" />
          <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-64 h-64 bg-cyan-500/5 rounded-full blur-3xl animate-pulse" />
        </div>

        {/* Navbar */}
        <Navbar />

        {/* Page Content */}
        <main className="relative">
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
              path="/inbox"
              element={
                <ProtectedRoute>
                  <PendingAppointments />
                </ProtectedRoute>
              }
            />
          </Routes>
        </main>

        {/* Footer */}
        <footer className="relative z-10 border-t border-white/5 mt-16">
          <div className="max-w-6xl mx-auto px-4 sm:px-6 py-6">
            <p className="text-center text-xs text-slate-500">
              Randevum © 2026 — Tüm hakları saklıdır
            </p>
          </div>
        </footer>
      </div>
    </BrowserRouter>
  );
}
