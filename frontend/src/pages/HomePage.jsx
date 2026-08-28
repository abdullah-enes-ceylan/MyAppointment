import { useState, useEffect, useMemo } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import api from "../api/axios";
import { useAuth } from "../context/AuthContext";
import BusinessCard from "../components/BusinessCard";
import { CATEGORIES, GENDERS, getCategoryLabel } from "../components/CategoryIcons";
import { setLocationLabel } from "../components/Navbar";

// Faz 2.11: tarayıcı konum izni reddedilirse/desteklenmezse düşülen
// sabit şehir listesi. Koordinatlar şehir merkezine yakın bir nokta --
// GPS kadar hassas değil ama "şehir çapında yakınımdakiler" için yeterli.
const CITIES = [
  { name: "İstanbul", lat: 41.0082, lng: 28.9784 },
  { name: "Ankara", lat: 39.9334, lng: 32.8597 },
  { name: "İzmir", lat: 38.4237, lng: 27.1428 },
  { name: "Bursa", lat: 40.1826, lng: 29.0665 },
  { name: "Antalya", lat: 36.8969, lng: 30.7133 },
  { name: "Diyarbakır", lat: 37.9144, lng: 40.2306 },
];

const GPS_RADIUS_KM = 20;
const CITY_RADIUS_KM = 50;

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

export default function HomePage() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const searchQuery = searchParams.get("q") ?? "";

  const [businesses, setBusinesses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeCategory, setActiveCategory] = useState("ALL");
  const [activeGender, setActiveGender] = useState("ALL");
  const [nearbyMode, setNearbyMode] = useState(false);
  const [showCityPicker, setShowCityPicker] = useState(false);
  const [locationError, setLocationError] = useState(null);
  const [favoriteIds, setFavoriteIds] = useState(new Set());
  const [earliestSlots, setEarliestSlots] = useState({});

  useEffect(() => {
    fetchBusinesses();
  }, []);

  useEffect(() => {
    if (searchParams.get("nearby") === "1") {
      const next = new URLSearchParams(searchParams);
      next.delete("nearby");
      setSearchParams(next, { replace: true });
      handleNearbyClick();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  useEffect(() => {
    if (!isAuthenticated) {
      setFavoriteIds(new Set());
      return;
    }
    api
      .get("/api/favorites/me")
      .then((res) => setFavoriteIds(new Set(res.data.map((b) => b.id))))
      .catch(() => {});
  }, [isAuthenticated]);

  // "Bugün En Erken" rozeti GERÇEK veriye dayanıyor: her işletmenin ilk
  // hizmeti için bugünün müsait slotları çekilip ilki alınıyor. Müsaitlik
  // yoksa rozet hiç çıkmıyor -- uydurma saat göstermiyoruz.
  useEffect(() => {
    if (businesses.length === 0) {
      setEarliestSlots({});
      return;
    }
    let cancelled = false;
    setEarliestSlots({});
    const date = todayIso();

    businesses.forEach(async (biz) => {
      const service = biz.serviceItems?.[0];
      if (!service) return;
      try {
        const res = await api.get("/api/appointments/available-slots", {
          params: { businessId: biz.id, serviceId: service.id, date },
        });
        if (!cancelled && res.data.length > 0) {
          setEarliestSlots((prev) => ({ ...prev, [biz.id]: res.data[0] }));
        }
      } catch {
        // Kapalı gün vb. -- "bugün müsaitlik yok" demek, hata değil.
      }
    });

    return () => {
      cancelled = true;
    };
  }, [businesses]);

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
      const url = categoryKey === "ALL" ? "/api/businesses" : `/api/businesses/category/${categoryKey}`;
      const res = await api.get(url);
      setBusinesses(res.data);
    } catch (err) {
      console.error("Filtreleme hatası:", err);
    } finally {
      setLoading(false);
    }
  }

  async function fetchNearby(lat, lng, radiusKm, label) {
    setLoading(true);
    setLocationError(null);
    try {
      const res = await api.get("/api/businesses/nearby", { params: { lat, lng, radiusKm } });
      setBusinesses(res.data.map((item) => ({ ...item.business, distanceKm: item.distanceKm })));
      setNearbyMode(true);
      setShowCityPicker(false);
      setLocationLabel(label);
    } catch {
      setLocationError("Yakınımdakiler yüklenirken hata oluştu.");
    } finally {
      setLoading(false);
    }
  }

  function handleNearbyClick() {
    setLocationError(null);
    if (!navigator.geolocation) {
      setShowCityPicker(true);
      return;
    }
    setLoading(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => fetchNearby(pos.coords.latitude, pos.coords.longitude, GPS_RADIUS_KM, "Yakınımdakiler"),
      () => {
        setShowCityPicker(true);
        setLoading(false);
      },
      { timeout: 8000 }
    );
  }

  // Arama tamamen istemci tarafında: backend'de arama ucu yok, liste zaten
  // yüklü. Ad, açıklama, adres ve kategori adında arıyor. Türkçe karakterler
  // için toLocaleLowerCase("tr") şart -- "İSTANBUL".toLowerCase() JS'te
  // "i̇stanbul" üretip eşleşmeyi bozuyor.
  const visibleBusinesses = useMemo(() => {
    const q = searchQuery.trim().toLocaleLowerCase("tr");

    return businesses.filter((b) => {
      // Hizmet grubu filtresi: "Erkek" seçilince UNISEX işletmeler DE
      // çıkıyor -- unisex bir salon erkeğe de hizmet veriyor, onu bu
      // listeden düşürmek eski modeldeki görünmezlik sorununu geri
      // getirirdi. Aynısı "Kadın" için de geçerli.
      const genderOk =
        activeGender === "ALL" ||
        b.servedGender === activeGender ||
        (activeGender !== "UNISEX" && b.servedGender === "UNISEX");

      if (!genderOk) return false;
      if (!q) return true;

      return [b.name, b.description, b.address, getCategoryLabel(b.category)]
        .filter(Boolean)
        .some((field) => field.toLocaleLowerCase("tr").includes(q));
    });
  }, [businesses, searchQuery, activeGender]);

  async function toggleFavorite(businessId) {
    const wasFavorited = favoriteIds.has(businessId);
    setFavoriteIds((prev) => {
      const next = new Set(prev);
      wasFavorited ? next.delete(businessId) : next.add(businessId);
      return next;
    });
    try {
      if (wasFavorited) await api.delete(`/api/favorites/${businessId}`);
      else await api.post(`/api/favorites/${businessId}`);
    } catch {
      setFavoriteIds((prev) => {
        const next = new Set(prev);
        wasFavorited ? next.add(businessId) : next.delete(businessId);
        return next;
      });
    }
  }

  return (
    <div className="bg-slate-50 min-h-[calc(100vh-3.5rem)]">
      {/* Kategori sekmeleri — beyaz şerit, mobilde yatay kaydırılabilir.
          nearbyMode'dayken hiçbir sekme aktif görünmüyor: o an aktif olan
          filtre kategori değil, konum. Sekmeye tıklamak konum modundan
          çıkmanın da yolu (filterByCategory nearbyMode'u false yapıyor). */}
      <div className="bg-white border-b border-slate-200 sticky top-14 sm:top-16 z-30">
        <div className="max-w-[1440px] mx-auto px-4 sm:px-6">
          <div className="flex gap-1 sm:gap-2 overflow-x-auto scrollbar-none">
            {CATEGORIES.map(({ key, label, Icon }) => {
              const active = !nearbyMode && activeCategory === key;
              return (
                <button
                  key={key}
                  onClick={() => filterByCategory(key)}
                  className={`relative shrink-0 flex flex-col sm:flex-row items-center gap-1 sm:gap-2 px-3 sm:px-4 py-2.5 my-2 rounded-xl text-xs sm:text-sm font-medium transition-colors cursor-pointer ${
                    active ? "bg-blue-50 text-blue-700" : "text-slate-500 hover:text-slate-900 hover:bg-slate-50"
                  }`}
                >
                  <Icon />
                  <span>{label}</span>
                  {active && (
                    <span className="absolute -bottom-2 left-2 right-2 h-[3px] rounded-full bg-blue-600" />
                  )}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      <div className="max-w-[1440px] mx-auto px-4 sm:px-6 py-6">
        {/* Hizmet grubu filtresi — kategoriden AYRI bir eksen olduğu için
            ayrı bir satırda duruyor (bkz. backend ServedGender). */}
        <div className="flex items-center gap-2 mb-5 overflow-x-auto scrollbar-none">
          <span className="text-xs font-semibold text-slate-500 shrink-0">Kime:</span>
          {GENDERS.map(({ key, label }) => (
            <button
              key={key}
              onClick={() => setActiveGender(key)}
              className={`shrink-0 px-3.5 py-1.5 text-xs font-medium rounded-full border transition-colors cursor-pointer ${
                activeGender === key
                  ? "bg-[#161b33] text-white border-[#161b33]"
                  : "bg-white text-slate-600 border-slate-200 hover:border-slate-300"
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {showCityPicker && (
          <div className="max-w-lg mb-5 bg-white border border-slate-200 shadow-sm rounded-2xl p-5">
            <p className="text-sm text-slate-600 mb-3">
              Konum izni alınamadı. Şehrinizi seçerek o çevredeki işletmeleri görebilirsiniz.
            </p>
            <div className="flex flex-wrap gap-2">
              {CITIES.map((city) => (
                <button
                  key={city.name}
                  onClick={() => fetchNearby(city.lat, city.lng, CITY_RADIUS_KM, city.name)}
                  className="px-3 py-1.5 text-xs font-medium bg-slate-50 border border-slate-200 rounded-lg text-slate-600 hover:text-slate-900 hover:border-blue-300 transition-all cursor-pointer"
                >
                  {city.name}
                </button>
              ))}
            </div>
            <button
              onClick={() => setShowCityPicker(false)}
              className="mt-3 text-xs text-slate-400 hover:text-slate-600 cursor-pointer"
            >
              Vazgeç
            </button>
          </div>
        )}

        {locationError && <p className="text-sm text-red-500 mb-4">{locationError}</p>}

        {(searchQuery || nearbyMode) && (
          <div className="flex items-center gap-2 mb-4 text-sm text-slate-600">
            <span>
              {searchQuery ? (
                <>
                  <strong className="text-slate-900">"{searchQuery}"</strong> için {visibleBusinesses.length} sonuç
                </>
              ) : (
                <>Konumunuza göre {visibleBusinesses.length} işletme</>
              )}
            </span>
            <button
              onClick={() => {
                setSearchParams({}, { replace: true });
                setLocationLabel(null);
                filterByCategory("ALL");
              }}
              className="text-blue-600 hover:underline cursor-pointer"
            >
              Temizle
            </button>
          </div>
        )}

        {loading ? (
          <div className="flex justify-center py-24">
            <div className="flex items-center gap-3 text-slate-400">
              <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              Yükleniyor...
            </div>
          </div>
        ) : visibleBusinesses.length === 0 ? (
          <div className="text-center py-24">
            <p className="text-5xl mb-4">🔍</p>
            <p className="text-slate-500 text-lg">
              {searchQuery
                ? "Aramanızla eşleşen işletme bulunamadı."
                : nearbyMode
                  ? "Yakınınızda işletme bulunamadı."
                  : "Bu kategoride işletme bulunamadı."}
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            {visibleBusinesses.map((biz) => (
              <BusinessCard
                key={biz.id}
                business={biz}
                earliestSlot={earliestSlots[biz.id]}
                isFavorited={favoriteIds.has(biz.id)}
                onToggleFavorite={isAuthenticated ? toggleFavorite : undefined}
                onOpen={(id) => navigate(`/business/${id}`)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
