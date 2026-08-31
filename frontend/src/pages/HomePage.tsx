import { useState, useEffect, useMemo, type FormEvent, type KeyboardEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import api from "../api/axios";
import { useAuth } from "../context/AuthContext";
import BusinessCard from "../components/BusinessCard";
import { CATEGORIES, GENDERS, getCategoryLabel } from "../components/CategoryIcons";
import { setLocationLabel as broadcastLocationLabel } from "../components/Navbar";
import type { BusinessCategory, BusinessDetailResponse, BusinessResponse, NearbyBusinessResponse, ServedGender, ServiceItemResponse } from "../types/api";

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

// businesses state'i iki farkli sekilde doluyor: normal/kategori modunda
// GET /api/businesses(/category/{cat}) -> BusinessDetailResponse (serviceItems
// gomulu), nearby modunda ise GET /api/businesses/nearby -> NearbyBusinessResponse'un
// SADECE business (serviceItems'siz BusinessResponse) + distanceKm'i alinip
// duz nesneye yayiliyor (bkz. fetchNearby). Bu yuzden ikisini de karsilayan
// tek bir tip: serviceItems VE distanceKm ikisi de opsiyonel.
type HomeBusiness = BusinessResponse & {
  serviceItems?: ServiceItemResponse[];
  distanceKm?: number;
};

export default function HomePage() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const searchQuery = searchParams.get("q") ?? "";

  const [businesses, setBusinesses] = useState<HomeBusiness[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeCategory, setActiveCategory] = useState<BusinessCategory | "ALL">("ALL");
  const [activeGender, setActiveGender] = useState<ServedGender | "ALL">("ALL");
  const [nearbyMode, setNearbyMode] = useState(false);
  const [showCityPicker, setShowCityPicker] = useState(false);
  const [locationError, setLocationError] = useState<string | null>(null);
  const [favoriteIds, setFavoriteIds] = useState<Set<number>>(new Set());
  const [earliestSlots, setEarliestSlots] = useState<Record<number, string>>({});
  // Konum etiketi + arama kutusunun anlik degeri. Hero kendi govdesinde
  // (HomePage) oldugu icin bunlar duz local state -- AMA Navbar'da da
  // kompakt bir konum/arama gosterimi oldugu icin (2. karsilastirma karari,
  // bkz. Navbar.tsx) konum etiketi degistiginde broadcastLocationLabel ile
  // Navbar'a da bildiriliyor (localStorage+custom event bridge).
  const [locationLabel, setLocationLabel] = useState<string | null>(null);
  const [searchInput, setSearchInput] = useState(searchQuery);

  useEffect(() => {
    fetchBusinesses();
  }, []);

  // Navbar'daki kompakt konum butonu, hangi sayfada olunursa olunsun
  // /?nearby=1'e yonlendirip buradan tetikliyor -- Navbar'in HomePage'in
  // handleNearbyClick'ine dogrudan erisimi yok (kardes bilesenler).
  useEffect(() => {
    if (searchParams.get("nearby") === "1") {
      const next = new URLSearchParams(searchParams);
      next.delete("nearby");
      setSearchParams(next, { replace: true });
      handleNearbyClick();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  // Arama kutusu URL'e yazılan q parametresiyle çalışıyor; kullanıcı geri
  // tuşuna basıp aramadan çıkarsa kutu da temizlensin (Navbar'daki kompakt
  // arama kutusu da ayni q parametresini okuyup yaziyor).
  useEffect(() => {
    setSearchInput(searchQuery);
  }, [searchQuery]);

  useEffect(() => {
    if (!isAuthenticated) {
      setFavoriteIds(new Set());
      return;
    }
    api
      .get<BusinessDetailResponse[]>("/api/favorites/me")
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
        const res = await api.get<string[]>("/api/appointments/available-slots", {
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
      const res = await api.get<BusinessDetailResponse[]>("/api/businesses");
      setBusinesses(res.data);
    } catch (err) {
      console.error("İşletmeler yüklenemedi:", err);
    } finally {
      setLoading(false);
    }
  }

  async function filterByCategory(categoryKey: BusinessCategory | "ALL") {
    setNearbyMode(false);
    setActiveCategory(categoryKey);
    setLoading(true);
    try {
      const url = categoryKey === "ALL" ? "/api/businesses" : `/api/businesses/category/${categoryKey}`;
      const res = await api.get<BusinessDetailResponse[]>(url);
      setBusinesses(res.data);
    } catch (err) {
      console.error("Filtreleme hatası:", err);
    } finally {
      setLoading(false);
    }
  }

  async function fetchNearby(lat: number, lng: number, radiusKm: number, label: string) {
    setLoading(true);
    setLocationError(null);
    try {
      const res = await api.get<NearbyBusinessResponse[]>("/api/businesses/nearby", { params: { lat, lng, radiusKm } });
      setBusinesses(res.data.map((item) => ({ ...item.business, distanceKm: item.distanceKm })));
      setNearbyMode(true);
      setShowCityPicker(false);
      setLocationLabel(label);
      broadcastLocationLabel(label);
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

  // Arama tamamen istemci tarafında (aşağıdaki visibleBusinesses zaten
  // yüklü listeyi filtreliyor) -- backend'de arama ucu yok. Sorgu URL'e
  // yazılıyor ki hem sayfa yenilenince korunsun hem de arama sonucu
  // paylaşılabilir/yer imine eklenebilir olsun (eskiden Navbar'daki
  // handleSearch, artık Hero burada olduğu için doğrudan burada).
  function handleSearchSubmit(e?: FormEvent) {
    e?.preventDefault();
    const trimmed = searchInput.trim();
    setSearchParams(trimmed ? { q: trimmed } : {});
  }

  function handleSearchKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter") handleSearchSubmit();
  }

  // Ad, açıklama, adres ve kategori adında arıyor. Türkçe karakterler için
  // toLocaleLowerCase("tr") şart -- "İSTANBUL".toLowerCase() JS'te
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

      // .filter(Boolean) description'daki null'i tur seviyesinde otomatik
      // elemiyor (TS'in "inferred predicate" ozelligi burada tetiklenmiyor,
      // test edildi) -- "?." ile gercek bir gozden kacan null durumunu
      // maskelemiyoruz, zaten filter(Boolean) sayesinde field hicbir zaman
      // bos/null degil, sadece tur bunu bilmiyor.
      return [b.name, b.description, b.address, getCategoryLabel(b.category)]
        .filter(Boolean)
        .some((field) => field?.toLocaleLowerCase("tr").includes(q));
    });
  }, [businesses, searchQuery, activeGender]);

  async function toggleFavorite(businessId: number) {
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
      {/* Hero — başlık, büyük pill arama+konum kutusu, cinsiyet filtresi.
          Google AI Studio prototipiyle karşılaştırma sonrası eklendi
          (2026-08-30): arama+konum artık Navbar'da değil burada -- Navbar
          bu yüzden tek satıra döndü (bkz. Navbar.tsx). Cinsiyet filtresi de
          eski ayrı satırından buraya taşındı, prototipteki HeroSection ile
          aynı gruplama. STICKY DEĞİL -- sadece aşağıdaki kategori şeridi
          sticky, Hero sayfayla birlikte kayıp gidiyor. */}
      <div className="relative overflow-hidden bg-gradient-to-b from-canvas via-canvas-soft to-canvas py-10 sm:py-14">
        <div className="relative max-w-3xl mx-auto px-4 sm:px-6 text-center">
          <h1 className="text-2xl sm:text-3xl md:text-[40px] font-bold text-slate-900 tracking-tight leading-tight mb-3">
            Güzellik ve Bakım Randevunuzu{" "}
            <span className="text-brand underline decoration-brand/30 decoration-wavy">Alın</span>
          </h1>
          <p className="text-sm sm:text-base text-slate-500 max-w-xl mx-auto mb-7">
            En iyi işletmeleri keşfedin, size uygun zamanı seçin ve hemen yerinizi ayırtın.
          </p>

          {/* Büyük pill arama + konum kutusu */}
          <form
            onSubmit={handleSearchSubmit}
            className="bg-white rounded-2xl md:rounded-full p-2 md:p-2.5 shadow-xl shadow-brand/10 border border-slate-200 flex flex-col md:flex-row items-center gap-2 md:gap-3 max-w-2xl mx-auto mb-6"
          >
            <div className="relative flex-1 w-full flex items-center pl-3">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="text-slate-400 shrink-0">
                <circle cx="11" cy="11" r="7" />
                <path d="M20 20l-3.5-3.5" strokeLinecap="round" />
              </svg>
              <input
                type="search"
                placeholder="İşletme, kuaför veya hizmet ara..."
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                onKeyDown={handleSearchKeyDown}
                className="w-full py-2.5 px-3 text-sm sm:text-base text-slate-900 placeholder-slate-400 bg-transparent outline-none"
              />
            </div>

            <div className="hidden md:block h-7 w-[1px] bg-slate-200" />

            <button
              type="button"
              onClick={handleNearbyClick}
              className="w-full md:w-auto flex items-center justify-center gap-1.5 px-4 py-2.5 rounded-xl md:rounded-full text-xs sm:text-sm font-medium bg-canvas-soft hover:bg-slate-200/70 text-slate-700 transition-colors cursor-pointer shrink-0"
            >
              <span className="shrink-0">📍</span>
              <span className="truncate max-w-[140px]">{locationLabel ?? "Yakınımdakiler"}</span>
            </button>

            <button
              type="submit"
              className="w-full md:w-auto flex items-center justify-center gap-2 px-6 py-3 rounded-xl md:rounded-full bg-brand hover:bg-brand-hover text-white text-sm sm:text-base font-semibold shadow-md shadow-brand/30 transition-all cursor-pointer shrink-0"
            >
              Ara
            </button>
          </form>

          {/* Cinsiyet filtresi — kategoriden AYRI bir eksen olduğu için ayrı
              bir grup (bkz. backend ServedGender). Eski yeri: kategori
              şeridinin altındaki içerik alanı; artık Hero'nun parçası. */}
          <div className="flex flex-col items-center gap-2.5">
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Kime</span>
            <div className="flex items-center justify-center gap-2 bg-white/80 p-1.5 rounded-full border border-slate-200 shadow-sm">
              {GENDERS.map(({ key, label }) => (
                <button
                  key={key}
                  onClick={() => setActiveGender(key)}
                  className={`px-4 py-1.5 rounded-full text-xs sm:text-sm font-medium transition-all cursor-pointer ${
                    activeGender === key
                      ? "bg-brand text-white shadow-sm"
                      : "text-slate-600 hover:text-slate-900 hover:bg-canvas-soft"
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Kategori sekmeleri — dikey ikon-kare kutucuklar (Google AI Studio
          prototipiyle karşılaştırma sonrası, 2026-08-30, eski yatay
          ikon+etiket pill stilinin yerine). Navbar'ın altında sticky duruyor
          -- Navbar artık tek satır olduğu için ofset onunla birebir aynı
          (h-14 sm:h-16).
          nearbyMode'dayken hiçbir sekme aktif görünmüyor: o an aktif olan
          filtre kategori değil, konum. Sekmeye tıklamak konum modundan
          çıkmanın da yolu (filterByCategory nearbyMode'u false yapıyor). */}
      <div className="bg-canvas border-b border-slate-200/80 sticky top-14 sm:top-16 z-30 py-4 sm:py-5">
        <div className="max-w-[1440px] mx-auto px-4 sm:px-6">
          <div className="flex items-center gap-3 sm:gap-6 md:gap-8 overflow-x-auto scrollbar-none sm:justify-center py-1">
            {CATEGORIES.map(({ key, label, Icon }) => {
              const active = !nearbyMode && activeCategory === key;
              return (
                <button
                  key={key}
                  onClick={() => filterByCategory(key)}
                  className="group/cat flex flex-col items-center gap-2 shrink-0 cursor-pointer"
                >
                  <div
                    className={`w-14 h-14 sm:w-16 sm:h-16 rounded-2xl flex items-center justify-center transition-all duration-200 ${
                      active
                        ? "bg-brand text-white shadow-lg shadow-brand/25 scale-105 ring-4 ring-brand/15"
                        : "bg-canvas-soft text-brand hover:bg-slate-200/70"
                    }`}
                  >
                    <Icon />
                  </div>
                  <span
                    className={`text-xs sm:text-sm font-medium transition-colors ${
                      active ? "text-brand font-bold" : "text-slate-600 group-hover/cat:text-slate-900"
                    }`}
                  >
                    {label}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      </div>

      <div className="max-w-[1440px] mx-auto px-4 sm:px-6 py-6">
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
                broadcastLocationLabel(null);
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

        {/* Güven rozetleri şeridi — Google AI Studio prototipinden alındı
            (2026-08-30), tamamen statik/veri bağımsız, filtre sonucundan
            etkilenmiyor. Backend'e ya da AuthContext'e hiç dokunmuyor. */}
        <section className="mt-12 bg-gradient-to-r from-canvas-soft to-canvas rounded-3xl p-6 sm:p-8 border border-slate-200">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {[
              {
                title: "Onaylı & Hijyenik İşletmeler",
                description: "Tüm işletmeler müşteri yorumları ve hijyen standartlarına göre düzenli denetlenir.",
                icon: (
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z" />
                    <path d="M9 12l2 2 4-4" />
                  </svg>
                ),
              },
              {
                title: "Anında Onaylı Randevu",
                description: "Telefonla beklemeden 7/24 dilediğiniz saat dilimini saniyeler içinde ayırtın.",
                icon: (
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                    <circle cx="12" cy="12" r="9" />
                    <path d="M12 7v5l3.5 2" />
                  </svg>
                ),
              },
              {
                title: "Şeffaf Fiyat, Kolay İptal",
                description: "Gizli ücret yok. Hizmet fiyatını görüp öyle randevu alırsınız, dilediğinizde iptal edersiniz.",
                icon: (
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                    <rect x="3" y="6" width="18" height="12" rx="2" />
                    <path d="M3 10h18" />
                    <path d="M7 15h4" />
                  </svg>
                ),
              },
            ].map((item) => (
              <div key={item.title} className="flex items-start gap-4">
                <div className="w-12 h-12 rounded-2xl bg-white text-brand flex items-center justify-center shadow-sm shrink-0">
                  {item.icon}
                </div>
                <div>
                  <h3 className="font-bold text-sm text-slate-900 mb-1">{item.title}</h3>
                  <p className="text-xs text-slate-500 leading-relaxed">{item.description}</p>
                </div>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}
