import { useState, useEffect, type ChangeEvent, type FormEvent } from "react";
import api from "../../api/axios";
import { getErrorMessage } from "../../api/errors";
import Toast from "../../components/Toast";
import type { ServiceItemRequest, ServiceItemResponse } from "../../types/api";

// Form alanlari controlled input oldugu icin string tutuluyor (price/
// durationInMinutes girilirken bos string olabilmeli); gonderilirken
// Number() ile ServiceItemRequest'e cevriliyor.
interface ServiceFormState {
  name: string;
  description: string;
  price: string;
  durationInMinutes: string;
}

const EMPTY_FORM: ServiceFormState = { name: "", description: "", price: "", durationInMinutes: "" };

interface ToastState {
  message: string;
  type: "success" | "error";
}

// Hizmet yönetimi — seçili işletmenin hizmetlerini listeler, ekleme/
// düzenleme/silme yapar. ServiceItemController create/update uçları hâlâ
// ham ServiceItem entity'si bekliyor (Faz 1.5 sadece Business için DTO
// getirdi) — bu yüzden gönderilen alanlar entity'nin setter'larıyla
// birebir eşleşiyor: name, description, price, durationInMinutes.
export default function ServicesTab({ businessId, suspended = false }: { businessId: number | null; suspended?: boolean }) {
  const [services, setServices] = useState<ServiceItemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<ToastState | null>(null);
  const [saving, setSaving] = useState(false);

  const [showAddForm, setShowAddForm] = useState(false);
  const [addForm, setAddForm] = useState<ServiceFormState>(EMPTY_FORM);

  const [editingId, setEditingId] = useState<number | null>(null);
  const [editForm, setEditForm] = useState<ServiceFormState>(EMPTY_FORM);

  const [deletingId, setDeletingId] = useState<number | null>(null);

  useEffect(() => {
    if (businessId) fetchServices();
  }, [businessId]);

  async function fetchServices() {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get<ServiceItemResponse[]>(`/api/service-items/business/${businessId}`);
      setServices(res.data);
    } catch {
      setError("Hizmetler yüklenirken hata oluştu.");
    } finally {
      setLoading(false);
    }
  }

  async function handleAddSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setSaving(true);
    try {
      const body: ServiceItemRequest = {
        name: addForm.name,
        description: addForm.description,
        price: Number(addForm.price),
        durationInMinutes: Number(addForm.durationInMinutes),
      };
      const res = await api.post<ServiceItemResponse>(`/api/service-items/create/${businessId}`, body);
      setServices((prev) => [...prev, res.data]);
      setAddForm(EMPTY_FORM);
      setShowAddForm(false);
      setToast({ message: "✅ Hizmet eklendi.", type: "success" });
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Hizmet eklenirken hata oluştu."), type: "error" });
    } finally {
      setSaving(false);
    }
  }

  function startEdit(service: ServiceItemResponse) {
    setEditingId(service.id);
    setEditForm({
      name: service.name,
      description: service.description,
      price: String(service.price),
      durationInMinutes: String(service.durationInMinutes),
    });
  }

  async function handleEditSubmit(e: FormEvent<HTMLFormElement>, serviceId: number) {
    e.preventDefault();
    setSaving(true);
    try {
      const body: ServiceItemRequest = {
        name: editForm.name,
        description: editForm.description,
        price: Number(editForm.price),
        durationInMinutes: Number(editForm.durationInMinutes),
      };
      const res = await api.put<ServiceItemResponse>(`/api/service-items/update/${serviceId}`, body);
      setServices((prev) => prev.map((s) => (s.id === serviceId ? res.data : s)));
      setEditingId(null);
      setToast({ message: "✅ Hizmet güncellendi.", type: "success" });
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Hizmet güncellenirken hata oluştu."), type: "error" });
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(serviceId: number) {
    setDeletingId(serviceId);
    try {
      await api.delete(`/api/service-items/delete/${serviceId}`);
      setServices((prev) => prev.filter((s) => s.id !== serviceId));
      setToast({ message: "🗑️ Hizmet silindi.", type: "success" });
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Hizmet silinirken hata oluştu."), type: "error" });
    } finally {
      setDeletingId(null);
    }
  }

  const inputClass =
    "w-full px-3 py-2 bg-bg-light border border-white/10 rounded-lg text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500/50 transition-all";

  return (
    <div>
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      {loading && (
        <div className="flex justify-center py-16">
          <div className="flex items-center gap-3 text-slate-400">
            <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24" fill="none">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
            Yükleniyor...
          </div>
        </div>
      )}

      {error && !loading && (
        <div className="text-center py-16">
          <p className="text-5xl mb-4">⚠️</p>
          <p className="text-red-400 text-lg mb-4">{error}</p>
          <button onClick={fetchServices} className="text-emerald-400 hover:text-emerald-300 text-sm font-medium cursor-pointer">
            Tekrar Dene
          </button>
        </div>
      )}

      {!loading && !error && (
        <div className="space-y-4">
          {/* Yeni hizmet ekle -- askidaysa buton hic gorunmuyor (bkz.
              BusinessPanelPage'deki salt-okunur banner). */}
          {suspended ? null : !showAddForm ? (
            <button
              onClick={() => setShowAddForm(true)}
              className="w-full py-3 text-sm font-medium text-emerald-400 border border-dashed border-emerald-500/30 rounded-xl hover:bg-emerald-500/5 hover:border-emerald-500/50 transition-all cursor-pointer"
            >
              + Yeni Hizmet Ekle
            </button>
          ) : (
            <form
              onSubmit={handleAddSubmit}
              className="bg-surface/80 border border-emerald-500/20 rounded-2xl p-5 space-y-3"
            >
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <input
                  type="text"
                  required
                  placeholder="Hizmet adı"
                  value={addForm.name}
                  onChange={(e: ChangeEvent<HTMLInputElement>) => setAddForm({ ...addForm, name: e.target.value })}
                  className={inputClass}
                />
                <input
                  type="text"
                  placeholder="Açıklama"
                  value={addForm.description}
                  onChange={(e: ChangeEvent<HTMLInputElement>) => setAddForm({ ...addForm, description: e.target.value })}
                  className={inputClass}
                />
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  required
                  placeholder="Fiyat (₺)"
                  value={addForm.price}
                  onChange={(e: ChangeEvent<HTMLInputElement>) => setAddForm({ ...addForm, price: e.target.value })}
                  className={inputClass}
                />
                <input
                  type="number"
                  min="1"
                  required
                  placeholder="Süre (dk)"
                  value={addForm.durationInMinutes}
                  onChange={(e: ChangeEvent<HTMLInputElement>) => setAddForm({ ...addForm, durationInMinutes: e.target.value })}
                  className={inputClass}
                />
              </div>
              <div className="flex gap-3">
                <button
                  type="submit"
                  disabled={saving}
                  className="flex-1 py-2.5 text-sm font-semibold text-white bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 rounded-xl disabled:opacity-50 transition-all cursor-pointer"
                >
                  {saving ? "Kaydediliyor..." : "Kaydet"}
                </button>
                <button
                  type="button"
                  onClick={() => { setShowAddForm(false); setAddForm(EMPTY_FORM); }}
                  className="px-4 py-2.5 text-sm font-medium text-slate-300 hover:text-white border border-white/10 rounded-xl transition-all cursor-pointer"
                >
                  Vazgeç
                </button>
              </div>
            </form>
          )}

          {/* Hizmet listesi */}
          {services.length === 0 ? (
            <div className="text-center py-12">
              <p className="text-slate-400 text-sm">Henüz hizmet eklenmemiş.</p>
            </div>
          ) : (
            services.map((service) => (
              <div
                key={service.id}
                className="bg-surface/80 backdrop-blur-sm border border-white/10 rounded-2xl p-5"
              >
                {editingId === service.id ? (
                  <form onSubmit={(e) => handleEditSubmit(e, service.id)} className="space-y-3">
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <input
                        type="text"
                        required
                        value={editForm.name}
                        onChange={(e: ChangeEvent<HTMLInputElement>) => setEditForm({ ...editForm, name: e.target.value })}
                        className={inputClass}
                      />
                      <input
                        type="text"
                        value={editForm.description}
                        onChange={(e: ChangeEvent<HTMLInputElement>) => setEditForm({ ...editForm, description: e.target.value })}
                        className={inputClass}
                      />
                      <input
                        type="number"
                        step="0.01"
                        min="0"
                        required
                        value={editForm.price}
                        onChange={(e: ChangeEvent<HTMLInputElement>) => setEditForm({ ...editForm, price: e.target.value })}
                        className={inputClass}
                      />
                      <input
                        type="number"
                        min="1"
                        required
                        value={editForm.durationInMinutes}
                        onChange={(e: ChangeEvent<HTMLInputElement>) => setEditForm({ ...editForm, durationInMinutes: e.target.value })}
                        className={inputClass}
                      />
                    </div>
                    <div className="flex gap-3">
                      <button
                        type="submit"
                        disabled={saving}
                        className="flex-1 py-2 text-sm font-semibold text-white bg-gradient-to-r from-emerald-600 to-teal-600 rounded-lg disabled:opacity-50 cursor-pointer"
                      >
                        {saving ? "Kaydediliyor..." : "Kaydet"}
                      </button>
                      <button
                        type="button"
                        onClick={() => setEditingId(null)}
                        className="px-4 py-2 text-sm font-medium text-slate-300 hover:text-white border border-white/10 rounded-lg cursor-pointer"
                      >
                        Vazgeç
                      </button>
                    </div>
                  </form>
                ) : (
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <p className="text-white font-semibold text-sm">{service.name}</p>
                      {service.description && (
                        <p className="text-slate-400 text-xs mt-1">{service.description}</p>
                      )}
                      <div className="flex items-center gap-3 text-xs text-slate-400 mt-2">
                        <span>💰 {service.price} ₺</span>
                        <span>⏱ {service.durationInMinutes} dk</span>
                      </div>
                    </div>
                    {!suspended && (
                      <div className="flex gap-2 shrink-0">
                        <button
                          onClick={() => startEdit(service)}
                          className="px-3 py-1.5 text-xs font-medium text-slate-300 hover:text-white border border-white/10 hover:border-white/20 rounded-lg transition-all cursor-pointer"
                        >
                          Düzenle
                        </button>
                        <button
                          onClick={() => handleDelete(service.id)}
                          disabled={deletingId === service.id}
                          className="px-3 py-1.5 text-xs font-medium text-red-400 hover:text-red-300 border border-red-500/20 hover:border-red-500/40 rounded-lg disabled:opacity-50 transition-all cursor-pointer"
                        >
                          {deletingId === service.id ? "Siliniyor..." : "Sil"}
                        </button>
                      </div>
                    )}
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
