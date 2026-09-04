import { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { ArrowLeft, Compass, Heart, Search, X } from "lucide-react";
import api from "../api/axios";
import BusinessCard from "../components/BusinessCard";
import { CATEGORIES, getCategoryLabel } from "../components/CategoryIcons";
import type { BusinessCategory, BusinessDetailResponse } from "../types/api";

// 2. Google AI Studio prototipiyle karşılaştırma sonrası (2026-09-04):
// koyu navy başlık bandı (kalp rozeti, sayaç, arama + kategori filtresi)
// eklendi. Filtreleme tamamen istemci tarafında -- liste zaten kullanıcının
// kendi favorilerine sınırlı, ayrı bir backend ucu gerekmiyor.
export default function FavoritesPage() {
  const navigate = useNavigate();
  const [businesses, setBusinesses] = useState<BusinessDetailResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [earliestSlots, setEarliestSlots] = useState<Record<number, string>>({});
  const [searchQuery, setSearchQuery] = useState("");
  const [activeCategory, setActiveCategory] = useState<BusinessCategory | "ALL">("ALL");

  useEffect(() => {
    fetchFavorites();
  }, []);

  async function fetchFavorites() {
    setLoading(true);
    try {
      const res = await api.get<BusinessDetailResponse[]>("/api/favorites/me");
      setBusinesses(res.data);
    } catch (err) {
      console.error("Favoriler yüklenemedi:", err);
    } finally {
      setLoading(false);
    }
  }

  // HomePage'deki aynı gerçek-veri prensibi: uydurma bir saat göstermek
  // yerine, mevcut olduğunda gerçek "bugün en erken" bilgisini çekiyoruz.
  useEffect(() => {
    if (businesses.length === 0) {
      setEarliestSlots({});
      return;
    }
    let cancelled = false;
    setEarliestSlots({});
    const date = new Date().toISOString().slice(0, 10);

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
        // Sessizce yut -- bkz. HomePage'deki aynı açıklama.
      }
    });

    return () => {
      cancelled = true;
    };
  }, [businesses]);

  // Favoriden çıkarınca kart listeden de kaybolur -- bu sayfada "favoride
  // değil" hali göstermenin bir anlamı yok, HomePage'deki gibi kalp
  // ikonunu boş göstermek yerine direkt listeden kaldırıyoruz.
  async function removeFavorite(businessId: number) {
    setBusinesses((prev) => prev.filter((b) => b.id !== businessId));
    try {
      await api.delete(`/api/favorites/${businessId}`);
    } catch {
      // Silme başarısız olduysa listeyi olduğu gibi yeniden çekip düzelt.
      fetchFavorites();
    }
  }

  const visibleBusinesses = useMemo(() => {
    const q = searchQuery.trim().toLocaleLowerCase("tr");
    return businesses.filter((b) => {
      if (activeCategory !== "ALL" && b.category !== activeCategory) return false;
      if (!q) return true;
      return [b.name, b.description, b.address].filter(Boolean).some((f) => f?.toLocaleLowerCase("tr").includes(q));
    });
  }, [businesses, searchQuery, activeCategory]);

  return (
    <div className="w-full min-h-[calc(100vh-4.5rem)] bg-slate-100/70 py-4 sm:py-8 px-3 sm:px-6 lg:px-8 flex flex-col items-center">
      <div className="w-full max-w-7xl">
        <div className="flex items-center justify-between mb-4 px-1">
          <button
            onClick={() => navigate("/")}
            className="inline-flex items-center gap-2 text-xs sm:text-sm font-bold text-slate-700 hover:text-slate-950 transition bg-white px-4 py-2 rounded-xl border border-slate-200/80 shadow-2xs hover:shadow-xs cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4 text-brand" />
            <span>Ana Sayfaya (Keşfet) Dön</span>
          </button>
          <span className="text-xs text-slate-500 font-medium hidden sm:inline">Randevum • Favorilerim</span>
        </div>

        {/* Koyu navy başlık bandı */}
        <div className="bg-[#0b1b2d] rounded-2xl sm:rounded-3xl p-6 sm:p-8 text-white mb-8 shadow-xl border border-white/10 relative overflow-hidden">
          <div className="absolute top-0 right-0 w-96 h-96 bg-rose-500/10 rounded-full blur-3xl -mr-20 -mt-20 pointer-events-none" />

          <div className="relative z-10 flex flex-col md:flex-row md:items-center md:justify-between gap-6">
            <div>
              <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-rose-500/15 text-rose-300 border border-rose-400/20 mb-3">
                <Heart className="w-3.5 h-3.5 fill-rose-400 text-rose-400" />
                <span>Kaydedilen Salonlar</span>
              </div>
              <h1 className="text-2xl sm:text-3xl font-black tracking-tight text-white">Favori Salonlarım</h1>
              <p className="text-xs sm:text-sm text-blue-200/80 mt-1 max-w-xl">
                Hızlı randevu almak için beğendiğiniz kuaför, berber ve güzellik merkezleri.
              </p>
            </div>

            <div className="flex items-center gap-3 bg-white/10 backdrop-blur-md p-3.5 rounded-2xl border border-white/15 shrink-0 self-start md:self-auto">
              <div className="w-10 h-10 rounded-xl bg-rose-500 text-white flex items-center justify-center shadow-sm">
                <Heart className="w-5 h-5 fill-white" />
              </div>
              <div>
                <div className="text-lg font-black text-white leading-none">{businesses.length}</div>
                <div className="text-[11px] font-semibold text-blue-200/70 mt-0.5">Kayıtlı İşletme</div>
              </div>
            </div>
          </div>

          {businesses.length > 0 && (
            <div className="mt-6 pt-5 border-t border-white/10 flex flex-col sm:flex-row items-center gap-3">
              <div className="relative w-full sm:max-w-md">
                <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Favorileriniz içinde ara..."
                  className="w-full bg-white text-slate-900 placeholder:text-slate-400 text-xs sm:text-sm pl-10 pr-8 py-2.5 rounded-xl border border-white/20 focus:outline-none focus:ring-2 focus:ring-rose-400 shadow-sm"
                />
                {searchQuery && (
                  <button
                    onClick={() => setSearchQuery("")}
                    className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 p-0.5"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>

              <div className="flex items-center gap-1.5 overflow-x-auto w-full pb-1 sm:pb-0 scrollbar-none">
                {(["ALL", ...CATEGORIES.filter((c) => c.key !== "ALL").map((c) => c.key)] as (BusinessCategory | "ALL")[]).map(
                  (key) => (
                    <button
                      key={key}
                      onClick={() => setActiveCategory(key)}
                      className={`px-3 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition cursor-pointer ${
                        activeCategory === key
                          ? "bg-white text-[#0b1b2d] font-bold shadow-xs"
                          : "bg-white/10 hover:bg-white/15 text-white/90 border border-white/10"
                      }`}
                    >
                      {getCategoryLabel(key)}
                    </button>
                  )
                )}
              </div>
            </div>
          )}
        </div>

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
          <div className="bg-white rounded-3xl p-8 sm:p-14 border border-slate-200/90 shadow-sm text-center max-w-lg mx-auto">
            <div className="w-16 h-16 rounded-3xl bg-rose-50 border border-rose-100 text-rose-500 flex items-center justify-center mx-auto mb-4 shadow-2xs">
              <Heart className="w-8 h-8 fill-rose-500/20" />
            </div>
            <h3 className="text-lg sm:text-xl font-extrabold text-slate-900">Henüz Favori Salonunuz Yok</h3>
            <p className="text-xs sm:text-sm text-slate-500 mt-2 leading-relaxed">
              Keşfet sayfasındaki kuaför, berber ve güzellik salonlarının üzerindeki kalp simgesine tıklayarak
              favorilerinize ekleyebilirsiniz.
            </p>
            <div className="mt-6">
              <button
                onClick={() => navigate("/")}
                className="inline-flex items-center gap-2 px-5 py-3 rounded-xl bg-[#0b1b2d] hover:bg-[#153254] text-white font-bold text-xs sm:text-sm transition shadow-md cursor-pointer"
              >
                <Compass className="w-4 h-4 text-sky-400" />
                <span>Salonları Keşfetmeye Başla</span>
              </button>
            </div>
          </div>
        ) : visibleBusinesses.length === 0 ? (
          <div className="bg-white rounded-3xl p-10 border border-slate-200/90 shadow-sm text-center max-w-md mx-auto">
            <Search className="w-10 h-10 text-slate-400 mx-auto mb-3" />
            <h4 className="font-bold text-base text-slate-900">Sonuç Bulunamadı</h4>
            <p className="text-xs text-slate-500 mt-1">"{searchQuery}" aramasına uygun favori salon bulunamadı.</p>
            <button
              onClick={() => {
                setSearchQuery("");
                setActiveCategory("ALL");
              }}
              className="mt-4 px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold text-xs transition cursor-pointer"
            >
              Filtreleri Temizle
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {visibleBusinesses.map((biz) => (
              <BusinessCard
                key={biz.id}
                business={biz}
                earliestSlot={earliestSlots[biz.id]}
                isFavorited={true}
                onToggleFavorite={removeFavorite}
                onOpen={(id) => navigate(`/business/${id}`)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
