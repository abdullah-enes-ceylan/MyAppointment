import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import api from "../api/axios";

const CATEGORIES = [
  { key: "ALL", label: "Tümü", icon: "🏢" },
  { key: "HAIRDRESSER", label: "Kuaför", icon: "💇" },
  { key: "BARBER", label: "Berber", icon: "💈" },
  { key: "BEAUTY_SALON", label: "Güzellik Salonu", icon: "💄" },
  { key: "SPA_WELLNESS", label: "Spa & Wellness", icon: "🧖" },
  { key: "NAIL_STUDIO", label: "Tırnak Stüdyosu", icon: "💅" },
  { key: "MAKEUP_STUDIO", label: "Makyaj Stüdyosu", icon: "🎨" },
  { key: "TATTOO_STUDIO", label: "Dövme Stüdyosu", icon: "🖋️" },
];

function getCategoryLabel(key) {
  const cat = CATEGORIES.find((c) => c.key === key);
  return cat ? cat.label : key;
}

function getCategoryIcon(key) {
  const cat = CATEGORIES.find((c) => c.key === key);
  return cat ? cat.icon : "🏢";
}

export default function HomePage() {
  const navigate = useNavigate();
  const [businesses, setBusinesses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeCategory, setActiveCategory] = useState("ALL");

  useEffect(() => {
    fetchBusinesses();
  }, []);

  async function fetchBusinesses() {
    setLoading(true);
    try {
      const res = await api.get("/api/businesses");
      setBusinesses(res.data);
    } catch (err) {
      console.error("İşletmeler yüklenemedi:", err);
    } finally {
      setLoading(false);
    }
  }

  async function filterByCategory(categoryKey) {
    setActiveCategory(categoryKey);
    setLoading(true);

    try {
      if (categoryKey === "ALL") {
        const res = await api.get("/api/businesses");
        setBusinesses(res.data);
      } else {
        const res = await api.get(`/api/businesses/category/${categoryKey}`);
        setBusinesses(res.data);
      }
    } catch (err) {
      console.error("Filtreleme hatası:", err);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8">
      {/* Hero Section */}
      <div className="text-center mb-10">
        <h1 className="text-3xl sm:text-4xl font-bold text-white mb-3">
          Randevunuzu <span className="text-transparent bg-clip-text bg-gradient-to-r from-emerald-400 to-teal-400">Kolayca</span> Alın
        </h1>
        <p className="text-slate-400 text-sm sm:text-base max-w-xl mx-auto">
          Çevrenizdeki işletmeleri keşfedin ve birkaç tıkla randevunuzu oluşturun.
        </p>
      </div>

      {/* Category Filters */}
      <div className="mb-8">
        <div className="flex flex-wrap gap-2 justify-center">
          {CATEGORIES.map((cat) => (
            <button
              key={cat.key}
              onClick={() => filterByCategory(cat.key)}
              className={`px-4 py-2 text-sm font-medium rounded-xl transition-all duration-200 cursor-pointer ${
                activeCategory === cat.key
                  ? "bg-gradient-to-r from-emerald-600 to-teal-600 text-white shadow-lg shadow-emerald-500/20"
                  : "bg-surface border border-white/10 text-slate-300 hover:text-white hover:border-white/20"
              }`}
            >
              <span className="mr-1.5">{cat.icon}</span>
              {cat.label}
            </button>
          ))}
        </div>
      </div>

      {/* Business Cards */}
      {loading ? (
        <div className="flex justify-center py-20">
          <div className="flex items-center gap-3 text-slate-400">
            <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
            Yükleniyor...
          </div>
        </div>
      ) : businesses.length === 0 ? (
        <div className="text-center py-20">
          <p className="text-5xl mb-4">🔍</p>
          <p className="text-slate-400 text-lg">Bu kategoride işletme bulunamadı.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
          {businesses.map((biz) => (
            <div
              key={biz.id}
              className="group bg-surface/80 backdrop-blur-sm border border-white/10 rounded-2xl overflow-hidden hover:border-emerald-500/30 hover:shadow-xl hover:shadow-emerald-500/5 transition-all duration-300"
            >
              {/* Card Header — Category Badge */}
              <div className="bg-gradient-to-r from-emerald-600/10 to-teal-600/10 px-5 py-3 border-b border-white/5">
                <span className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-400">
                  <span>{getCategoryIcon(biz.category)}</span>
                  {getCategoryLabel(biz.category)}
                </span>
              </div>

              {/* Card Body */}
              <div className="p-5 space-y-3">
                <h3 className="text-lg font-semibold text-white group-hover:text-emerald-400 transition-colors">
                  {biz.name}
                </h3>

                {biz.address && (
                  <div className="flex items-start gap-2 text-sm text-slate-400">
                    <span className="mt-0.5">📍</span>
                    <span>{biz.address}</span>
                  </div>
                )}

                {biz.phone && (
                  <div className="flex items-center gap-2 text-sm text-slate-400">
                    <span>📞</span>
                    <span>{biz.phone}</span>
                  </div>
                )}

                {(biz.openTime || biz.closeTime) && (
                  <div className="flex items-center gap-2 text-sm text-slate-400">
                    <span>🕐</span>
                    <span>
                      {biz.openTime?.slice(0, 5)} — {biz.closeTime?.slice(0, 5)}
                    </span>
                  </div>
                )}

                {biz.description && (
                  <p className="text-xs text-slate-500 line-clamp-2">{biz.description}</p>
                )}
              </div>

              {/* Card Footer */}
              <div className="px-5 pb-5">
                <button
                  onClick={() => navigate(`/business/${biz.id}`)}
                  className="w-full py-2.5 text-sm font-medium text-emerald-400 border border-emerald-500/30 rounded-xl hover:bg-emerald-500/10 hover:text-emerald-300 transition-all duration-200 cursor-pointer"
                >
                  📅 Randevu Al
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
