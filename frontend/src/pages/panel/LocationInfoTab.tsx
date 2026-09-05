import { useState, useEffect, type ChangeEvent, type FormEvent } from "react";
import { MapPin } from "lucide-react";
import api from "../../api/axios";
import { getValidationErrors } from "../../api/errors";
import Toast from "../../components/Toast";
import LocationPicker from "../../components/LocationPicker";
import { CATEGORIES, GENDERS } from "../../components/CategoryIcons";
import type { BusinessCategory, BusinessDetailResponse, BusinessRequest, ServedGender } from "../../types/api";

// Eskiden iki ayri sekmeydi (LocationTab: harita/enlem-boylam, InfoTab:
// ad/adres/telefon/aciklama/kategori/hizmet-grubu/otomatik-onay) -- kullanici
// istegiyle (2026-09-05) TEK sekmede birlestirildi, TEK "Bilgileri Guncelle"
// akisiyla kaydediliyor. Kapak fotografi BILEREK burada DEGIL, ayri
// GalleryTab'a tasindi (bkz. o dosya).
//
// Mockup'ta ayrica "Ilce/Semt" (ayri alan) ve "WhatsApp Randevu Hatti" alani
// vardi -- backend'de bu alanlar HENUZ YOK (Business entity'sinde tek bir
// duz "address" ve tek bir "phone" var). Kullanici karariyla (2026-09-05)
// simdilik EKLENMEDI, mevcut Adres/Telefon alanlarıyla ilerleniyor; ayri bir
// ROADMAP maddesi olarak not dusuldu.
interface FormState {
  name: string;
  address: string;
  phone: string;
  description: string;
  category: BusinessCategory;
  servedGender: ServedGender;
  autoApprove: boolean;
}

interface ToastState {
  message: string;
  type: "success" | "error";
}

const fieldClass =
  "w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand/30 focus:border-brand/50 transition-all";
const labelClass = "block text-xs font-semibold text-slate-500 mb-1.5 tracking-wide uppercase";

export default function LocationInfoTab({ businessId, suspended = false }: { businessId: number | null; suspended?: boolean }) {
  const [business, setBusiness] = useState<BusinessDetailResponse | null>(null);
  const [form, setForm] = useState<FormState | null>(null);
  const [pendingLocation, setPendingLocation] = useState<[number, number] | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [toast, setToast] = useState<ToastState | null>(null);

  useEffect(() => {
    if (businessId) fetchBusiness();
  }, [businessId]);

  async function fetchBusiness() {
    setLoading(true);
    setPendingLocation(null);
    try {
      const res = await api.get<BusinessDetailResponse>(`/api/businesses/${businessId}`);
      setBusiness(res.data);
      setForm({
        name: res.data.name ?? "",
        address: res.data.address ?? "",
        phone: res.data.phone ?? "",
        description: res.data.description ?? "",
        category: res.data.category,
        servedGender: res.data.servedGender,
        autoApprove: res.data.autoApprove,
      });
    } catch {
      setToast({ message: "İşletme bilgileri yüklenemedi.", type: "error" });
    } finally {
      setLoading(false);
    }
  }

  async function handleSave(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!form || !business) return;
    setSaving(true);
    setErrors({});
    try {
      const body: BusinessRequest = {
        ...form,
        openTime: business.openTime ?? "",
        closeTime: business.closeTime ?? "",
        latitude: pendingLocation ? pendingLocation[0] : business.latitude,
        longitude: pendingLocation ? pendingLocation[1] : business.longitude,
      };
      const res = await api.put<BusinessDetailResponse>(`/api/businesses/${businessId}`, body);
      setBusiness(res.data);
      setPendingLocation(null);
      setToast({ message: "✅ Bilgiler güncellendi.", type: "success" });
    } catch (err) {
      const { fieldErrors, message } = getValidationErrors(err, "Kaydedilirken hata oluştu.");
      setErrors(fieldErrors);
      if (Object.keys(fieldErrors).length === 0) {
        setToast({ message, type: "error" });
      }
    } finally {
      setSaving(false);
    }
  }

  if (loading || !form || !business) {
    return <div className="text-center py-16 text-slate-400 text-sm">Yükleniyor...</div>;
  }

  const displayLat = pendingLocation ? pendingLocation[0] : business.latitude;
  const displayLng = pendingLocation ? pendingLocation[1] : business.longitude;

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      <form onSubmit={handleSave}>
        <div className="flex items-center justify-between px-6 py-5 border-b border-slate-100">
          <div>
            <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
              <MapPin className="w-5 h-5 text-brand" />
              Konum &amp; İletişim Bilgileri
            </h2>
            <p className="text-sm text-slate-500 mt-0.5">Müşterilerin salona kolayca ulaşabilmesi için adres detayları</p>
          </div>
          <button
            type="submit"
            disabled={saving || suspended}
            className="px-4 py-2 text-sm font-semibold text-white bg-brand hover:bg-brand-hover rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-colors cursor-pointer shrink-0"
          >
            {saving ? "Kaydediliyor..." : "Bilgileri Güncelle"}
          </button>
        </div>

        <div className="p-6 space-y-5">
          <div>
            <label htmlFor="biz-name" className={labelClass}>İşletme Adı</label>
            <input
              id="biz-name"
              value={form.name}
              onChange={(e: ChangeEvent<HTMLInputElement>) => setForm({ ...form, name: e.target.value })}
              className={fieldClass}
            />
            {errors.name && <p className="mt-1 text-xs text-red-500">{errors.name}</p>}
          </div>

          <div>
            <label htmlFor="biz-address" className={labelClass}>Açık Adres</label>
            <textarea
              id="biz-address"
              rows={2}
              value={form.address}
              onChange={(e: ChangeEvent<HTMLTextAreaElement>) => setForm({ ...form, address: e.target.value })}
              className={fieldClass}
            />
            {errors.address && <p className="mt-1 text-xs text-red-500">{errors.address}</p>}
          </div>

          <div>
            <label htmlFor="biz-phone" className={labelClass}>İletişim Telefonu</label>
            <input
              id="biz-phone"
              value={form.phone}
              onChange={(e: ChangeEvent<HTMLInputElement>) => setForm({ ...form, phone: e.target.value })}
              className={fieldClass}
            />
          </div>

          <div>
            <label htmlFor="biz-desc" className={labelClass}>Açıklama</label>
            <textarea
              id="biz-desc"
              rows={2}
              value={form.description}
              onChange={(e: ChangeEvent<HTMLTextAreaElement>) => setForm({ ...form, description: e.target.value })}
              className={fieldClass}
            />
          </div>

          <div>
            <label htmlFor="biz-cat" className={labelClass}>Kategori</label>
            <select
              id="biz-cat"
              value={form.category}
              onChange={(e: ChangeEvent<HTMLSelectElement>) => setForm({ ...form, category: e.target.value as BusinessCategory })}
              className={`${fieldClass} cursor-pointer`}
            >
              {CATEGORIES.filter((c) => c.key !== "ALL").map((c) => (
                <option key={c.key} value={c.key}>{c.label}</option>
              ))}
            </select>
          </div>

          <div>
            <span className={labelClass}>Kime Hizmet Veriyorsunuz?</span>
            <div className="flex flex-wrap gap-2">
              {GENDERS.filter((g) => g.key !== "ALL").map((g) => (
                <button
                  key={g.key}
                  type="button"
                  onClick={() => setForm({ ...form, servedGender: g.key as ServedGender })}
                  className={`px-4 py-2 text-sm font-medium rounded-xl border transition-colors cursor-pointer ${
                    form.servedGender === g.key
                      ? "bg-brand text-white border-brand"
                      : "bg-slate-50 text-slate-600 border-slate-200 hover:border-slate-300"
                  }`}
                >
                  {g.label}
                </button>
              ))}
            </div>
            {errors.servedGender && <p className="mt-1 text-xs text-red-500">{errors.servedGender}</p>}
          </div>

          <div className="bg-slate-50 border border-slate-200 rounded-xl px-4 py-3">
            <label className="flex items-center justify-between gap-4 cursor-pointer">
              <span>
                <span className="block text-sm font-medium text-slate-900">Otomatik Onay</span>
                <span className="block text-xs text-slate-500 mt-0.5">
                  Açıksa yeni randevu talepleri İstek Kutusu'na düşmeden doğrudan onaylanır.
                </span>
              </span>
              <input
                type="checkbox"
                checked={form.autoApprove}
                onChange={(e: ChangeEvent<HTMLInputElement>) => setForm({ ...form, autoApprove: e.target.checked })}
                className="w-5 h-5 shrink-0 accent-brand cursor-pointer"
              />
            </label>
          </div>

          <div>
            <span className={labelClass}>Haritadaki Konum</span>
            <p className="text-sm text-slate-500 mb-2">
              Haritada işletmenizin bulunduğu noktaya tıklayın — müşterilerin "yakınımdakiler" aramasında kullanılır.
            </p>
            <LocationPicker
              latitude={business.latitude}
              longitude={business.longitude}
              onChange={(lat, lng) => setPendingLocation([lat, lng])}
            />
            <div className="mt-2 text-sm">
              {displayLat != null && displayLng != null ? (
                <span className="text-slate-500">📍 {displayLat.toFixed(5)}, {displayLng.toFixed(5)}</span>
              ) : (
                <span className="text-amber-600">⚠️ Konum girilmediği sürece işletmeniz "yakınımdakiler" aramasında görünmez.</span>
              )}
            </div>
          </div>
        </div>
      </form>
    </div>
  );
}
