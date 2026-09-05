import { useState, type ChangeEvent, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { Building2 } from "lucide-react";
import api from "../api/axios";
import { getValidationErrors } from "../api/errors";
import Toast from "../components/Toast";
import { useAuth } from "../context/AuthContext";
import { CATEGORIES, GENDERS } from "../components/CategoryIcons";
import type { BusinessCategory, BusinessDetailResponse, BusinessRequest, ServedGender } from "../types/api";

interface FormState {
  name: string;
  address: string;
  phone: string;
  description: string;
  openTime: string;
  closeTime: string;
  category: BusinessCategory;
  servedGender: ServedGender;
}

interface ToastState {
  message: string;
  type: "success" | "error";
}

const EMPTY_FORM: FormState = {
  name: "",
  address: "",
  phone: "",
  description: "",
  // Makul varsayilanlar -- gunluk saatler zaten panelde (Calisma Saatleri
  // sekmesi) ayrica, gun bazinda yonetiliyor; buradaki sadece genel
  // acilis/kapanis, bos birakilip @NotNull'a takilmasin diye dolduruluyor.
  openTime: "09:00",
  closeTime: "18:00",
  category: "HAIRDRESSER",
  servedGender: "UNISEX",
};

const fieldClass =
  "w-full px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand/30 focus:border-brand/50 transition-all";
const labelClass = "block text-xs font-semibold text-slate-500 mb-1.5 tracking-wide uppercase";

// İşletme sahibi olma akışı — backend'de POST /api/businesses/create çoktan
// vardı (BusinessService.createBusiness, başarılı olursa kullanıcıyı
// otomatik BUSINESS_OWNER'a yükseltiyor) ama bunu çağıran HİÇBİR frontend
// ekranı yoktu: geliştirme boyunca hep DatabaseSeeder'ın hazır işletmeleri
// kullanıldığı için "gerçek bir kullanıcı işletme açar" akışı hiç
// tıklanmamıştı (bkz. NOTLAR.md). Prod'da seeder hiç çalışmadığı için
// bu ekran olmadan canlıda TEK bir işletme bile oluşamazdı.
//
// Konum (latitude/longitude) BİLEREK burada YOK — LocationInfoTab'daki ile
// aynı gerekçe: kategori kadar acil değil, panelde harita üzerinden ayrıca
// girilir. Otomatik onay da BİLEREK yok, güvenli varsayılan (istek modu)
// ile açılır, panelden istenirse değiştirilir — ilk formu gereksiz
// büyütmemek için sadece işletmeyi var eden asgari alanlar burada.
export default function CreateBusinessPage() {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [toast, setToast] = useState<ToastState | null>(null);

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setSaving(true);
    setErrors({});
    try {
      const body: BusinessRequest = {
        ...form,
        autoApprove: false,
      };
      await api.post<BusinessDetailResponse>("/api/businesses/create", body);
      setToast({
        message: "🎉 İşletmeniz oluşturuldu! Panele erişebilmek için tekrar giriş yapmanız gerekiyor.",
        type: "success",
      });
      // AuthContext'teki belgelenmiş karar: rol backend'de değişince JWT
      // (dolayısıyla frontend'in bildiği rol) kullanıcı yeniden giriş
      // yapana kadar eskisini taşımaya devam eder -- bu yüzden burada
      // "sihirli" bir token yenileme YOK, bilinçli olarak çıkış yapıp
      // giriş sayfasına yönlendiriyoruz.
      setTimeout(() => {
        logout();
        navigate("/login");
      }, 2000);
    } catch (err) {
      const { fieldErrors, message } = getValidationErrors(err, "İşletme oluşturulurken hata oluştu.");
      setErrors(fieldErrors);
      if (Object.keys(fieldErrors).length === 0) {
        setToast({ message, type: "error" });
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="bg-slate-50 min-h-[calc(100vh-4rem)]">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      <div className="max-w-2xl mx-auto px-4 sm:px-6 py-8">
        <div className="mb-6">
          <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 flex items-center gap-2">
            <Building2 className="w-7 h-7 text-brand" />
            İşletmenizi Ekleyin
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            Temel bilgileri girin, hizmetler ve çalışma saatleri gibi ayrıntıları daha sonra panelden düzenleyebilirsiniz.
          </p>
        </div>

        <form onSubmit={handleSubmit} className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 space-y-5">
          <div>
            <label htmlFor="biz-name" className={labelClass}>İşletme Adı</label>
            <input
              id="biz-name"
              required
              value={form.name}
              onChange={(e: ChangeEvent<HTMLInputElement>) => setForm({ ...form, name: e.target.value })}
              placeholder="Stil Kuaför"
              className={fieldClass}
            />
            {errors.name && <p className="mt-1 text-xs text-red-500">{errors.name}</p>}
          </div>

          <div>
            <label htmlFor="biz-address" className={labelClass}>Açık Adres</label>
            <textarea
              id="biz-address"
              required
              rows={2}
              value={form.address}
              onChange={(e: ChangeEvent<HTMLTextAreaElement>) => setForm({ ...form, address: e.target.value })}
              placeholder="Bağdat Caddesi No: 123, Kadıköy/İstanbul"
              className={fieldClass}
            />
            {errors.address && <p className="mt-1 text-xs text-red-500">{errors.address}</p>}
          </div>

          <div>
            <label htmlFor="biz-phone" className={labelClass}>İletişim Telefonu (opsiyonel)</label>
            <input
              id="biz-phone"
              value={form.phone}
              onChange={(e: ChangeEvent<HTMLInputElement>) => setForm({ ...form, phone: e.target.value })}
              placeholder="0212 555 00 00"
              className={fieldClass}
            />
            {errors.phone && <p className="mt-1 text-xs text-red-500">{errors.phone}</p>}
          </div>

          <div>
            <label htmlFor="biz-desc" className={labelClass}>Açıklama (opsiyonel)</label>
            <textarea
              id="biz-desc"
              rows={2}
              value={form.description}
              onChange={(e: ChangeEvent<HTMLTextAreaElement>) => setForm({ ...form, description: e.target.value })}
              placeholder="Müşterilerinize kısaca kendinizi tanıtın"
              className={fieldClass}
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="biz-open" className={labelClass}>Açılış Saati</label>
              <input
                id="biz-open"
                type="time"
                required
                value={form.openTime}
                onChange={(e: ChangeEvent<HTMLInputElement>) => setForm({ ...form, openTime: e.target.value })}
                className={fieldClass}
              />
              {errors.openTime && <p className="mt-1 text-xs text-red-500">{errors.openTime}</p>}
            </div>
            <div>
              <label htmlFor="biz-close" className={labelClass}>Kapanış Saati</label>
              <input
                id="biz-close"
                type="time"
                required
                value={form.closeTime}
                onChange={(e: ChangeEvent<HTMLInputElement>) => setForm({ ...form, closeTime: e.target.value })}
                className={fieldClass}
              />
              {errors.closeTime && <p className="mt-1 text-xs text-red-500">{errors.closeTime}</p>}
            </div>
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
            {errors.category && <p className="mt-1 text-xs text-red-500">{errors.category}</p>}
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

          <button
            type="submit"
            disabled={saving}
            className="w-full py-3.5 bg-brand hover:bg-brand-hover text-white font-semibold text-sm rounded-xl shadow-lg shadow-brand/20 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-300 transform hover:scale-[1.02] active:scale-[0.98] cursor-pointer"
          >
            {saving ? "Oluşturuluyor..." : "İşletmeyi Oluştur"}
          </button>
        </form>
      </div>
    </div>
  );
}
