import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import api from "../api/axios";
import StarRating from "../components/StarRating";

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

// Faz 2.11: tarayıcı konum izni reddedilirse/desteklenmezse düşülen
// sabit şehir listesi. Koordinatlar şehir merkezine yakın bir nokta --
// GPS kadar hassas değil ama "şehir çapında yakınımdakiler" için yeterli
// (bkz. aşağıdaki CITY_RADIUS_KM, GPS'ten daha geniş bir yarıçap kullanıyor).
const CITIES = [
  { name: "İstanbul", lat: 41.0082, lng: 28.9784 },
  { name: "Ankara", lat: 39.9334, lng: 32.8597 },
  { name: "İzmir", lat: 38.4237, lng: 27.1428 },
  { name: "Bursa", lat: 40.1826, lng: 29.0665 },
  { name: "Antalya", lat: 36.8969, lng: 30.7133 },
];

const GPS_RADIUS_KM = 20;
const CITY_RADIUS_KM = 50;

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
  const [nearbyMode, setNearbyMode] = useState(false);
  const [nearbyLoading, setNearbyLoading] = useState(false);
  const [showCityPicker, setShowCityPicker] = useState(false);
  const [locationError, setLocationError] = useState(null);

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
    setNearbyMode(false);
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

  // /api/businesses/nearby, {business, distanceKm} sarmalı içinde dönüyor
  // (bkz. NearbyBusinessResponse) -- kart render kodunun DEĞİŞMEDEN kalması
  // için business alanı düzleştirilip distanceKm onun üstüne ekleniyor,
  // böylece "biz.name", "biz.distanceKm" gibi tek seviyeli erişim korunuyor.
  async function fetchNearby(lat, lng, radiusKm) {
    setNearbyLoading(true);
    setLocationError(null);
    try {
      const res = await api.get("/api/businesses/nearby", { params: { lat, lng, radiusKm } });
      const flattened = res.data.map((item) => ({ ...item.business, distanceKm: item.distanceKm }));
      setBusinesses(flattened);
      setNearbyMode(true);
      setShowCityPicker(false);
    } catch (err) {
      setLocationError("Yakınımdakiler yüklenirken hata oluştu.");
    } finally {
      setNearbyLoading(false);
      setLoading(false);
    }
  }

  function handleNearbyClick() {
    setLoading(true);
    setLocationError(null);

    if (!navigator.geolocation) {
      setShowCityPicker(true);
      setLoading(false);
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        fetchNearby(position.coords.latitude, position.coords.longitude, GPS_RADIUS_KM);
      },
      () => {
        // İzin reddedildi ya da konum alınamadı -- şehir seçimine düş.
        setShowCityPicker(true);
        setLoading(false);
      },
      { timeout: 8000 }
    );
  }

  function handleCitySelect(city) {
    fetchNearby(city.lat, city.lng, CITY_RADIUS_KM);
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

      {/* Nearby Button */}
      <div className="flex justify-center mb-4">
        <button
          onClick={handleNearbyClick}
          disabled={nearbyLoading}
          className={`px-5 py-2.5 text-sm font-semibold rounded-xl transition-all duration-200 cursor-pointer disabled:opacity-50 ${
            nearbyMode
              ? "bg-gradient-to-r from-amber-500 to-orange-500 text-white shadow-lg shadow-amber-500/20"
              : "bg-surface border border-amber-500/30 text-amber-400 hover:bg-amber-500/10"
          }`}
        >
          {nearbyLoading ? "📍 Konum alınıyor..." : "📍 Yakınımdakiler"}
        </button>
      </div>

      {/* City Picker Fallback — konum izni reddedilirse/desteklenmezse */}
      {showCityPicker && (
        <div className="max-w-md mx-auto mb-6 bg-surface/80 border border-amber-500/20 rounded-2xl p-5 text-center">
          <p className="text-sm text-slate-300 mb-3">
            Konum izni alınamadı. Şehrinizi seçerek o çevredeki işletmeleri görebilirsiniz.
          </p>
          <div className="flex flex-wrap gap-2 justify-center">
            {CITIES.map((city) => (
              <button
                key={city.name}
                onClick={() => handleCitySelect(city)}
                className="px-3 py-1.5 text-xs font-medium bg-bg-light border border-white/10 rounded-lg text-slate-300 hover:text-white hover:border-amber-500/30 transition-all cursor-pointer"
              >
                {city.name}
              </button>
            ))}
          </div>
          <button
            onClick={() => setShowCityPicker(false)}
            className="mt-3 text-xs text-slate-500 hover:text-slate-300 cursor-pointer"
          >
            Vazgeç
          </button>
        </div>
      )}

      {locationError && (
        <p className="text-center text-sm text-red-400 mb-4">{locationError}</p>
      )}

      {/* Category Filters — nearbyMode'dayken de HER ZAMAN görünür kalır:
          aksi halde kullanıcı "Yakınımdakiler" moduna girdikten sonra
          kategori listesine dönecek bir çıkış yolu bulamazdı. Herhangi
          bir kategoriye tıklamak filterByCategory içinde nearbyMode'u
          zaten false yapıyor. nearbyMode'dayken hiçbir buton "aktif"
          görünmüyor (activeCategory ile eşleşme aranmıyor) -- o an aktif
          olan filtre kategori değil, konum. */}
      <div className="mb-8">
        <div className="flex flex-wrap gap-2 justify-center">
          {CATEGORIES.map((cat) => (
            <button
              key={cat.key}
              onClick={() => filterByCategory(cat.key)}
              className={`px-4 py-2 text-sm font-medium rounded-xl transition-all duration-200 cursor-pointer ${
                !nearbyMode && activeCategory === cat.key
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
          <p className="text-slate-400 text-lg">
            {nearbyMode ? "Yakınınızda işletme bulunamadı." : "Bu kategoride işletme bulunamadı."}
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
          {businesses.map((biz) => (
            <div
              key={biz.id}
              className="group bg-surface/80 backdrop-blur-sm border border-white/10 rounded-2xl overflow-hidden hover:border-emerald-500/30 hover:shadow-xl hover:shadow-emerald-500/5 transition-all duration-300"
            >
              {/* Card Header — Category Badge */}
              <div className="bg-gradient-to-r from-emerald-600/10 to-teal-600/10 px-5 py-3 border-b border-white/5 flex items-center justify-between">
                <span className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-400">
                  <span>{getCategoryIcon(biz.category)}</span>
                  {getCategoryLabel(biz.category)}
                </span>
                {biz.distanceKm != null && (
                  <span className="text-xs font-medium text-amber-400">
                    📏 {biz.distanceKm.toFixed(1)} km
                  </span>
                )}
              </div>

              {/* Card Body */}
              <div className="p-5 space-y-3">
                <h3 className="text-lg font-semibold text-white group-hover:text-emerald-400 transition-colors">
                  {biz.name}
                </h3>

                {biz.reviewCount > 0 ? (
                  <div className="flex items-center gap-1.5 text-sm">
                    <StarRating value={biz.averageRating} size="text-sm" />
                    <span className="text-slate-400 text-xs">
                      {biz.averageRating.toFixed(1)} ({biz.reviewCount})
                    </span>
                  </div>
                ) : (
                  <p className="text-xs text-slate-500 italic">Henüz yorum yok</p>
                )}

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
