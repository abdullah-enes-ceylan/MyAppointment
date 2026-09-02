import { useState, useEffect, useRef, type ChangeEvent, type FormEvent } from "react";
import api from "../../api/axios";
import { getErrorMessage, getValidationErrors } from "../../api/errors";
import Toast from "../../components/Toast";
import { CATEGORIES, GENDERS } from "../../components/CategoryIcons";
import { resolvePhotoUrl } from "../../utils/photo";
import type { BusinessCategory, BusinessDetailResponse, BusinessRequest, BusinessResponse, ServedGender } from "../../types/api";

// Backend'in kabul ettigi bicimlerle birebir -- BusinessPhotoProperties.
// allowedInputFormats (JPEG, PNG) ile ayni kume, sadece MIME karsiliklari.
const ACCEPTED_PHOTO_TYPES = "image/jpeg,image/png";

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
export default function InfoTab({ businessId, suspended = false }: { businessId: number | null; suspended?: boolean }) {
  const [business, setBusiness] = useState<BusinessDetailResponse | null>(null);
  const [form, setForm] = useState<InfoFormState | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [toast, setToast] = useState<ToastState | null>(null);

  // Kapak fotografi -- secilen dosyanin ONIZLEMESI (henuz yuklenmeden),
  // yukleme sirasindaki yukleniyor durumu. business.coverPhotoCardUrl
  // (zaten kayitli olan) ile photoPreview (yeni secilen, henuz kaydedilmemis)
  // BILEREK ayri tutuluyor -- yukleme basarisiz olursa eski fotograf
  // gostermeye devam etmeli, secilen dosya "kaybolmus" gibi durmamali.
  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [photoPreview, setPhotoPreview] = useState<string | null>(null);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [removingPhoto, setRemovingPhoto] = useState(false);
  // Kalici olmayan disk senaryosunda (bkz. CLAUDE.md karar tablosu) DB'de
  // coverPhotoCardUrl dururken dosya diskten gidebilir -- BusinessCard/
  // BusinessDetailPage'deki (PR5) ayni gerekce: onError olmadan tarayici
  // <img>'i DOM'da tutup kirik resim ikonu gosterir. Yeni bir dosya
  // secildiginde ya da isletme verisi yenilendiginde sifirlanir (asagida).
  const [imgFailed, setImgFailed] = useState(false);
  const photoInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (businessId) fetchBusiness();
  }, [businessId]);

  // Secilen dosya icin URL.createObjectURL ile uretilen blob URL'i, bilesen
  // unmount olduğunda ya da yeni bir dosya secildiginde serbest birakilmali
  // -- aksi halde tarayici belleginde birikir (klasik object URL sizintisi).
  useEffect(() => {
    return () => {
      if (photoPreview) URL.revokeObjectURL(photoPreview);
    };
  }, [photoPreview]);

  function handlePhotoSelect(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    if (photoPreview) URL.revokeObjectURL(photoPreview);
    setPhotoFile(file);
    setPhotoPreview(file ? URL.createObjectURL(file) : null);
    setImgFailed(false);
  }

  async function handlePhotoUpload() {
    if (!photoFile || !businessId) return;
    setUploadingPhoto(true);
    try {
      const formData = new FormData();
      formData.append("file", photoFile);
      // axios instance'i varsayilan olarak "Content-Type: application/json"
      // tasiyor (bkz. api/axios.ts) -- bu FormData govdeleri icin YANLIS ve
      // gercekten kirici: tarayici, Content-Type ONCEDEN (defaults uzerinden
      // bile) set edilmisse kendi boundary'li multipart baslığini ASLA
      // uretmiyor, backend "Current request is not a multipart request"
      // ile patliyor (bu varsayimdan degil, tarayicida canli denenip
      // gozlemlenmis bir hatadan biliniyor). Header'i `undefined` yaparak
      // KALDIRIYORUZ ki tarayici kendi boundary'sini uretsin.
      const res = await api.post<BusinessResponse>(`/api/businesses/${businessId}/photo`, formData, {
        headers: { "Content-Type": undefined },
      });
      setToast({ message: "✅ Kapak fotoğrafı güncellendi.", type: "success" });
      if (photoPreview) URL.revokeObjectURL(photoPreview);
      setPhotoFile(null);
      setPhotoPreview(null);
      if (photoInputRef.current) photoInputRef.current.value = "";
      setImgFailed(false);
      setBusiness((prev) => (prev ? { ...prev, coverPhotoCardUrl: res.data.coverPhotoCardUrl } : prev));
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Fotoğraf yüklenirken hata oluştu."), type: "error" });
    } finally {
      setUploadingPhoto(false);
    }
  }

  // DELETE ucu IDEMPOTENT ve her zaman 204 dondugu icin burada ozel bir hata
  // dali yok -- StaffTab.handleDelete'teki "Siliniyor..." deseniyle ayni.
  async function handleRemovePhoto() {
    if (!businessId) return;
    setRemovingPhoto(true);
    try {
      await api.delete(`/api/businesses/${businessId}/photo`);
      setToast({ message: "✅ Kapak fotoğrafı kaldırıldı.", type: "success" });
      setBusiness((prev) => (prev ? { ...prev, coverPhotoCardUrl: null } : prev));
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Fotoğraf kaldırılırken hata oluştu."), type: "error" });
    } finally {
      setRemovingPhoto(false);
    }
  }

  async function fetchBusiness() {
    setLoading(true);
    setImgFailed(false);
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

  if (loading || !form || !business) {
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

      {/* Kapak fotografi -- BILEREK ayri, handleSave'in disinda: bagimsiz bir
          islem (kendi yukleme ucu var, bkz. BusinessPhotoService), form
          alanlarindan biri gibi "Kaydet"e bagli degil. */}
      <div>
        <label className="block text-xs font-medium text-slate-400 mb-1.5">Kapak Fotoğrafı</label>
        <div className="flex items-center gap-4">
          <div className="w-32 h-24 rounded-xl overflow-hidden bg-gradient-to-br from-slate-700 to-slate-800 border border-white/10 shrink-0 flex items-center justify-center">
            {(photoPreview || business.coverPhotoCardUrl) && !imgFailed ? (
              <img
                src={photoPreview ?? resolvePhotoUrl(business.coverPhotoCardUrl) ?? undefined}
                alt="Kapak fotoğrafı önizleme"
                onError={() => setImgFailed(true)}
                className="w-full h-full object-cover"
              />
            ) : (
              <span className="text-2xl opacity-40">🖼️</span>
            )}
          </div>
          <div className="flex-1 space-y-2">
            <input
              ref={photoInputRef}
              type="file"
              accept={ACCEPTED_PHOTO_TYPES}
              onChange={handlePhotoSelect}
              className="block w-full text-xs text-slate-400 file:mr-3 file:py-2 file:px-4 file:rounded-lg file:border-0 file:text-xs file:font-medium file:bg-white/10 file:text-white hover:file:bg-white/20 file:cursor-pointer cursor-pointer"
            />
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={handlePhotoUpload}
                disabled={!photoFile || uploadingPhoto || suspended}
                className="px-4 py-2 text-xs font-semibold text-white bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 rounded-lg disabled:opacity-50 disabled:cursor-not-allowed transition-all cursor-pointer"
              >
                {uploadingPhoto ? "Yükleniyor..." : "Fotoğrafı Yükle"}
              </button>
              {/* Sadece kayitli bir fotograf VARSA gorunur -- henuz hic
                  yuklenmemis bir isletmede "kaldir" anlamsiz olurdu. */}
              {business.coverPhotoCardUrl && (
                <button
                  type="button"
                  onClick={handleRemovePhoto}
                  disabled={removingPhoto || uploadingPhoto || suspended}
                  className="px-4 py-2 text-xs font-semibold text-red-400 bg-red-500/10 hover:bg-red-500/20 border border-red-500/20 rounded-lg disabled:opacity-50 disabled:cursor-not-allowed transition-all cursor-pointer"
                >
                  {removingPhoto ? "Kaldırılıyor..." : "Fotoğrafı Kaldır"}
                </button>
              )}
            </div>
            <p className="text-xs text-slate-500">JPEG veya PNG, en fazla 5 MB.</p>
          </div>
        </div>
      </div>

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
          disabled={saving || suspended}
          className="px-5 py-2.5 text-sm font-semibold text-white bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-all cursor-pointer"
        >
          {saving ? "Kaydediliyor..." : "Kaydet"}
        </button>
      </form>
    </div>
  );
}
