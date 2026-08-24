import { useState, useEffect } from "react";
import api from "../../api/axios";
import InboxTab from "./InboxTab";
import ApprovedTab from "./ApprovedTab";
import ServicesTab from "./ServicesTab";
import StaffTab from "./StaffTab";
import WorkingHoursTab from "./WorkingHoursTab";
import LocationTab from "./LocationTab";
import InfoTab from "./InfoTab";

const TABS = [
  { key: "inbox", label: "📥 İstek Kutusu" },
  { key: "approved", label: "✅ Onaylananlar" },
  { key: "services", label: "✂️ Hizmetler" },
  { key: "staff", label: "👥 Personel" },
  { key: "hours", label: "🕒 Çalışma Saatleri" },
  { key: "location", label: "📍 Konum" },
  { key: "info", label: "🏢 Bilgiler" },
];

export default function BusinessPanelPage() {
  const [businesses, setBusinesses] = useState([]);
  const [selectedBusinessId, setSelectedBusinessId] = useState(null);
  const [activeTab, setActiveTab] = useState("inbox");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchBusinesses();
  }, []);

  async function fetchBusinesses() {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get("/api/businesses/my");
      setBusinesses(res.data);
      if (res.data.length > 0) {
        setSelectedBusinessId(res.data[0].id);
      }
    } catch (err) {
      setError("İşletmeleriniz yüklenirken hata oluştu.");
    } finally {
      setLoading(false);
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center py-20">
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

  if (error) {
    return (
      <div className="text-center py-16">
        <p className="text-5xl mb-4">⚠️</p>
        <p className="text-red-400 text-lg mb-4">{error}</p>
        <button
          onClick={fetchBusinesses}
          className="text-emerald-400 hover:text-emerald-300 text-sm font-medium cursor-pointer"
        >
          Tekrar Dene
        </button>
      </div>
    );
  }

  if (businesses.length === 0) {
    return (
      <div className="text-center py-20 max-w-lg mx-auto px-4">
        <div className="w-20 h-20 bg-slate-500/10 rounded-full flex items-center justify-center mx-auto mb-5">
          <span className="text-4xl">🏢</span>
        </div>
        <p className="text-white text-lg font-medium mb-1">Henüz bir işletmeniz yok</p>
        <p className="text-slate-400 text-sm">
          İşletme oluşturma ekranı yakında eklenecek. Şimdilik veritabanı üzerinden bir işletme
          sahibi olmanız gerekiyor.
        </p>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl sm:text-3xl font-bold text-white flex items-center gap-3">
          <span className="w-10 h-10 bg-gradient-to-br from-emerald-500 to-teal-600 rounded-xl flex items-center justify-center shadow-lg shadow-emerald-500/20">
            🏢
          </span>
          İşletme Paneli
        </h1>
      </div>

      {/* İşletme Seçici — birden fazla işletme varsa */}
      {businesses.length > 1 && (
        <div className="mb-6">
          <label className="block text-xs font-medium text-slate-500 uppercase tracking-wider mb-1.5">
            İşletme
          </label>
          <select
            value={selectedBusinessId ?? ""}
            onChange={(e) => setSelectedBusinessId(Number(e.target.value))}
            className="w-full sm:w-72 px-4 py-2.5 bg-surface border border-white/10 rounded-xl text-white text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500/50 transition-all cursor-pointer"
          >
            {businesses.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </select>
        </div>
      )}
      {businesses.length === 1 && (
        <p className="text-slate-400 text-sm mb-6">{businesses[0].name}</p>
      )}

      {/* Sekmeler */}
      <div className="flex gap-2 mb-6 border-b border-white/10 overflow-x-auto">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`px-4 py-2.5 text-sm font-medium whitespace-nowrap border-b-2 transition-colors cursor-pointer ${
              activeTab === tab.key
                ? "border-emerald-500 text-white"
                : "border-transparent text-slate-400 hover:text-white"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Sekme İçeriği */}
      {activeTab === "inbox" && <InboxTab businessId={selectedBusinessId} />}
      {activeTab === "approved" && <ApprovedTab businessId={selectedBusinessId} />}
      {activeTab === "services" && <ServicesTab businessId={selectedBusinessId} />}
      {activeTab === "staff" && <StaffTab businessId={selectedBusinessId} />}
      {activeTab === "hours" && <WorkingHoursTab businessId={selectedBusinessId} />}
      {activeTab === "location" && <LocationTab businessId={selectedBusinessId} />}
      {activeTab === "info" && <InfoTab businessId={selectedBusinessId} />}
    </div>
  );
}
