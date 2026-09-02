import { useState, useEffect } from "react";
import api from "../../api/axios";
import { getErrorMessage } from "../../api/errors";
import Toast from "../../components/Toast";
import LocationPicker from "../../components/LocationPicker";
import type { BusinessDetailResponse, BusinessRequest } from "../../types/api";

interface ToastState {
  message: string;
  type: "success" | "error";
}

// İşletme konumu — Faz 2.8. Mevcut PUT /api/businesses/{id} akışı
// kullanılıyor (Faz 1.5), ama BusinessMapper.applyToEntity TÜM alanları
// (name/address/... dahil) request'ten kopyalıyor -- sadece latitude/
// longitude göndersek diğer alanlar null'a düşerdi. Bu yüzden önce
// işletmenin GÜNCEL tüm bilgisi çekilip, PUT'ta olduğu gibi geri
// gönderiliyor, sadece konum değişiyor.
export default function LocationTab({ businessId, suspended = false }: { businessId: number | null; suspended?: boolean }) {
  const [business, setBusiness] = useState<BusinessDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<ToastState | null>(null);
  const [saving, setSaving] = useState(false);
  const [pendingLocation, setPendingLocation] = useState<[number, number] | null>(null);

  useEffect(() => {
    if (businessId) fetchBusiness();
  }, [businessId]);

  async function fetchBusiness() {
    setLoading(true);
    setError(null);
    setPendingLocation(null);
    try {
      const res = await api.get<BusinessDetailResponse>(`/api/businesses/${businessId}`);
      setBusiness(res.data);
    } catch {
      setError("İşletme bilgileri yüklenirken hata oluştu.");
    } finally {
      setLoading(false);
    }
  }

  async function handleSave() {
    if (!pendingLocation || !business) return;
    setSaving(true);
    try {
      const body: BusinessRequest = {
        name: business.name,
        address: business.address,
        phone: business.phone,
        description: business.description,
        openTime: business.openTime ?? "",
        closeTime: business.closeTime ?? "",
        category: business.category,
        // servedGender de geçmek ZORUNDA: applyToEntity tüm alanları
        // request'ten kopyalıyor, buradan göndermezsek işletmenin hizmet
        // grubu her konum kaydında sıfırlanırdı (ayrıca @NotNull olduğu
        // için istek 400 dönerdi).
        servedGender: business.servedGender,
        latitude: pendingLocation[0],
        longitude: pendingLocation[1],
      };
      const res = await api.put<BusinessDetailResponse>(`/api/businesses/${businessId}`, body);
      setBusiness((prev) => (prev ? { ...prev, latitude: res.data.latitude, longitude: res.data.longitude } : prev));
      setPendingLocation(null);
      setToast({ message: "✅ Konum kaydedildi.", type: "success" });
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Konum kaydedilirken hata oluştu."), type: "error" });
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center py-16">
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

  if (error || !business) {
    return (
      <div className="text-center py-16">
        <p className="text-5xl mb-4">⚠️</p>
        <p className="text-red-400 text-lg mb-4">{error}</p>
        <button onClick={fetchBusiness} className="text-emerald-400 hover:text-emerald-300 text-sm font-medium cursor-pointer">
          Tekrar Dene
        </button>
      </div>
    );
  }

  const hasLocation = business.latitude != null && business.longitude != null;
  const displayLat = pendingLocation ? pendingLocation[0] : business.latitude;
  const displayLng = pendingLocation ? pendingLocation[1] : business.longitude;

  return (
    <div className="space-y-4">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      <p className="text-sm text-slate-400">
        Haritada işletmenizin bulunduğu noktaya tıklayın. Bu konum, müşterilerin "yakınımdakiler"
        aramasında kullanılacak.
      </p>

      <LocationPicker
        latitude={business.latitude}
        longitude={business.longitude}
        onChange={(lat, lng) => setPendingLocation([lat, lng])}
      />

      <div className="flex items-center justify-between bg-surface/80 border border-white/10 rounded-xl px-4 py-3">
        <div className="text-sm">
          {displayLat != null && displayLng != null ? (
            <span className="text-slate-300">
              📍 {displayLat.toFixed(5)}, {displayLng.toFixed(5)}
            </span>
          ) : (
            <span className="text-slate-500 italic">Henüz konum seçilmedi</span>
          )}
        </div>
        <button
          onClick={handleSave}
          disabled={!pendingLocation || saving || suspended}
          className="px-4 py-2 text-sm font-semibold text-white bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-all cursor-pointer"
        >
          {saving ? "Kaydediliyor..." : "Kaydet"}
        </button>
      </div>

      {!hasLocation && !pendingLocation && (
        <p className="text-xs text-amber-400">
          ⚠️ Konum girilmediği sürece işletmeniz "yakınımdakiler" aramasında görünmez.
        </p>
      )}
    </div>
  );
}
