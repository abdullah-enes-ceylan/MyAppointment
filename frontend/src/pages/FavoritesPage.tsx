import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import api from "../api/axios";
import BusinessCard from "../components/BusinessCard";
import type { BusinessDetailResponse } from "../types/api";

// HomePage'in "İşletme Keşfet" paneliyle aynı görsel dil (açık zemin,
// beyaz kart container'ı) -- kategori/yakınımdakiler filtreleri burada
// yok, liste zaten kullanıcının kendi seçtiği işletmelerle sınırlı.
export default function FavoritesPage() {
  const navigate = useNavigate();
  const [businesses, setBusinesses] = useState<BusinessDetailResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [earliestSlots, setEarliestSlots] = useState<Record<number, string>>({});

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

  return (
    <div className="bg-slate-50 min-h-[calc(100vh-4rem)]">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 py-8">
        <div className="mb-6">
          <h1 className="text-2xl sm:text-3xl font-bold text-slate-900">Favorilerim</h1>
          <p className="text-slate-500 text-sm mt-1">Kalp ikonuyla işaretlediğiniz işletmeler.</p>
        </div>

        <div className="bg-white border border-slate-200 shadow-sm rounded-2xl p-5 sm:p-6">
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
              <p className="text-5xl mb-4">🤍</p>
              <p className="text-slate-500 text-lg">Henüz favori işletmeniz yok.</p>
              <button
                onClick={() => navigate("/")}
                className="mt-4 text-sm text-brand font-medium hover:underline cursor-pointer"
              >
                İşletmeleri keşfet →
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
              {businesses.map((biz) => (
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
    </div>
  );
}
