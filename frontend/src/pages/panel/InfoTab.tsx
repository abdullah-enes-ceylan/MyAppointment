import { useState, useEffect, type ChangeEvent, type FormEvent } from "react";
import api from "../../api/axios";
import { getValidationErrors } from "../../api/errors";
import Toast from "../../components/Toast";
import { CATEGORIES, GENDERS } from "../../components/CategoryIcons";
import type { BusinessCategory, BusinessDetailResponse, BusinessRequest, ServedGender } from "../../types/api";

// Bu sekmede duzenlenen alt kume -- BusinessRequest'in tamami degil,
// openTime/closeTime/latitude/longitude submit sirasinda business'tan
// (degismeden) tekrar ekleniyor (bkz. handleSave).
interface InfoFormState {
  name: string;
  address: string;
  phone: string;
  description: string;
  category: BusinessCategory;
  servedGender: ServedGender;
}

interface ToastState {
  message: string;
  type: "success" | "error";
}

// İşletme temel bilgileri. Bu sekme YENİ: daha önce işletme sahibinin
// adını/kategorisini değiştirebileceği hiçbir ekran yoktu, sadece konum
// vardı (LocationTab). Hizmet grubu (servedGender) alanı eklenince bir
// yerden ayarlanması gerekti ve bu boşluk ortaya çıktı.
//
// LocationTab'daki aynı uyarı burada da geçerli: BusinessMapper.applyToEntity
// TÜM alanları request'ten kopyalıyor, bu yüzden değiştirmediğimiz alanları
// (saatler, konum) da olduğu gibi geri göndermek zorundayız -- yoksa null'a
// düşerler.
export default function InfoTab({ businessId }: { businessId: number | null }) {
  const [business, setBusiness] = useState<BusinessDetailResponse | null>(null);
  const [form, setForm] = useState<InfoFormState | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [toast, setToast] = useState<ToastState | null>(null);

  useEffect(() => {
    if (businessId) fetchBusiness();
  }, [businessId]);

  async function fetchBusiness() {
    setLoading(true);
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
        // Bu sekmede düzenlenmeyen ama request'te zorunlu olan alanlar
        openTime: business.openTime ?? "",
        closeTime: business.closeTime ?? "",
        latitude: business.latitude,
        longitude: business.longitude,
      };
      const res = await api.put<BusinessDetailResponse>(`/api/businesses/${businessId}`, body);
      setBusiness(res.data);
      setToast({ message: "✅ İşletme bilgileri güncellendi.", type: "success" });
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

  if (loading || !form) {
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

  const field = "w-full px-4 py-2.5 bg-bg-light border border-white/10 rounded-xl text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 transition-all";

  return (
    <div className="max-w-2xl space-y-4">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      <form onSubmit={handleSave} className="space-y-4">
        <div>
          <label htmlFor="biz-name" className="block text-xs font-medium text-slate-400 mb-1.5">İşletme Adı</label>
          <input
            id="biz-name"
            value={form.name}
            onChange={(e: ChangeEvent<HTMLInputElement>) => setForm({ ...form, name: e.target.value })}
            className={field}
          />
          {errors.name && <p className="mt-1 text-xs text-red-400">{errors.name}</p>}
        </div>

        <div>
          <label htmlFor="biz-address" className="block text-xs font-medium text-slate-400 mb-1.5">Adres</label>
          <input
            id="biz-address"
            value={form.address}
            onChange={(e: ChangeEvent<HTMLInputElement>) => setForm({ ...form, address: e.target.value })}
            className={field}
          />
          {errors.address && <p className="mt-1 text-xs text-red-400">{errors.address}</p>}
        </div>

        <div>
          <label htmlFor="biz-phone" className="block text-xs font-medium text-slate-400 mb-1.5">Telefon</label>
          <input
            id="biz-phone"
            value={form.phone}
            onChange={(e: ChangeEvent<HTMLInputElement>) => setForm({ ...form, phone: e.target.value })}
            className={field}
          />
        </div>

        <div>
          <label htmlFor="biz-desc" className="block text-xs font-medium text-slate-400 mb-1.5">Açıklama</label>
          <textarea
            id="biz-desc"
            rows={2}
            value={form.description}
            onChange={(e: ChangeEvent<HTMLTextAreaElement>) => setForm({ ...form, description: e.target.value })}
            className={field}
          />
        </div>

        <div>
          <label htmlFor="biz-cat" className="block text-xs font-medium text-slate-400 mb-1.5">Kategori</label>
          <select
            id="biz-cat"
            value={form.category}
            onChange={(e: ChangeEvent<HTMLSelectElement>) => setForm({ ...form, category: e.target.value as BusinessCategory })}
            className={`${field} cursor-pointer`}
          >
            {CATEGORIES.filter((c) => c.key !== "ALL").map((c) => (
              <option key={c.key} value={c.key}>{c.label}</option>
            ))}
          </select>
        </div>

        {/* Hizmet grubu — kategoriden ayrı seçiliyor. Berber/erkek kuaförü
            ayrımı artık kategoride değil burada. */}
        <div>
          <span className="block text-xs font-medium text-slate-400 mb-1.5">Kime hizmet veriyorsunuz?</span>
          <div className="flex flex-wrap gap-2">
            {GENDERS.filter((g) => g.key !== "ALL").map((g) => (
              <button
                key={g.key}
                type="button"
                onClick={() => setForm({ ...form, servedGender: g.key as ServedGender })}
                className={`px-4 py-2 text-sm font-medium rounded-xl border transition-colors cursor-pointer ${
                  form.servedGender === g.key
                    ? "bg-emerald-600 text-white border-emerald-600"
                    : "bg-bg-light text-slate-300 border-white/10 hover:border-white/20"
                }`}
              >
                {g.label}
              </button>
            ))}
          </div>
          <p className="mt-2 text-xs text-slate-500">
            Sadece erkeğe hizmet veriyorsanız "Erkek" seçin — böylece kadın müşteriler
            size boşuna randevu talebi göndermez. İkisine de hizmet veriyorsanız "Unisex".
          </p>
          {errors.servedGender && <p className="mt-1 text-xs text-red-400">{errors.servedGender}</p>}
        </div>

        <button
          type="submit"
          disabled={saving}
          className="px-5 py-2.5 text-sm font-semibold text-white bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-all cursor-pointer"
        >
          {saving ? "Kaydediliyor..." : "Kaydet"}
        </button>
      </form>
    </div>
  );
}
