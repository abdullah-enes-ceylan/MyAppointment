import { useState, useEffect, type ChangeEvent, type FormEvent } from "react";
import api from "../../api/axios";
import { getErrorMessage } from "../../api/errors";
import Toast from "../../components/Toast";
import type { ServiceItemResponse, StaffRequest, StaffResponse } from "../../types/api";

// StaffRequest'in kendisi form state olarak dogrudan kullanilabiliyor --
// ServicesTab'daki gibi string'e cevrilmesi gereken bir alan yok
// (serviceIds zaten number[]).
const EMPTY_FORM: StaffRequest = { name: "", serviceIds: [] };

interface ToastState {
  message: string;
  type: "success" | "error";
}

// Personel yönetimi — Faz 2.3'ün backend'i (StaffController) kullanılıyor.
// MÜŞTERİ TARAFINDA personel seçimi/görünürlüğü YOK (bkz. CLAUDE.md karar
// tablosu, 2026-08-23) — bu ekran SADECE işletme sahibi için, kimin hangi
// hizmeti verdiğini yönetmek amacıyla. Randevu ataması backend'de görünmez
// şekilde otomatik yapılıyor (Faz 2.9).
interface StaffTabProps {
  businessId: number | null;
  suspended?: boolean;
  onCountChange?: (count: number) => void;
}

export default function StaffTab({ businessId, suspended = false, onCountChange }: StaffTabProps) {
  const [staff, setStaff] = useState<StaffResponse[]>([]);
  const [services, setServices] = useState<ServiceItemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<ToastState | null>(null);
  const [saving, setSaving] = useState(false);

  const [showAddForm, setShowAddForm] = useState(false);
  const [addForm, setAddForm] = useState<StaffRequest>(EMPTY_FORM);

  const [editingId, setEditingId] = useState<number | null>(null);
  const [editForm, setEditForm] = useState<StaffRequest>(EMPTY_FORM);

  const [deletingId, setDeletingId] = useState<number | null>(null);

  useEffect(() => {
    onCountChange?.(staff.length);
  }, [staff, onCountChange]);

  useEffect(() => {
    if (businessId) fetchAll();
  }, [businessId]);

  async function fetchAll() {
    setLoading(true);
    setError(null);
    try {
      const [staffRes, servicesRes] = await Promise.all([
        api.get<StaffResponse[]>(`/api/staff/business/${businessId}`),
        api.get<ServiceItemResponse[]>(`/api/service-items/business/${businessId}`),
      ]);
      setStaff(staffRes.data);
      setServices(servicesRes.data);
    } catch {
      setError("Personel bilgileri yüklenirken hata oluştu.");
    } finally {
      setLoading(false);
    }
  }

  function toggleServiceId(currentIds: number[], serviceId: number): number[] {
    return currentIds.includes(serviceId)
      ? currentIds.filter((id) => id !== serviceId)
      : [...currentIds, serviceId];
  }

  async function handleAddSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setSaving(true);
    try {
      const res = await api.post<StaffResponse>(`/api/staff/create/${businessId}`, addForm);
      setStaff((prev) => [...prev, res.data]);
      setAddForm(EMPTY_FORM);
      setShowAddForm(false);
      setToast({ message: "✅ Personel eklendi.", type: "success" });
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Personel eklenirken hata oluştu."), type: "error" });
    } finally {
      setSaving(false);
    }
  }

  function startEdit(member: StaffResponse) {
    setEditingId(member.id);
    setEditForm({ name: member.name, serviceIds: member.services.map((s) => s.id) });
  }

  async function handleEditSubmit(e: FormEvent<HTMLFormElement>, staffId: number) {
    e.preventDefault();
    setSaving(true);
    try {
      const res = await api.put<StaffResponse>(`/api/staff/update/${staffId}`, editForm);
      setStaff((prev) => prev.map((s) => (s.id === staffId ? res.data : s)));
      setEditingId(null);
      setToast({ message: "✅ Personel güncellendi.", type: "success" });
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Personel güncellenirken hata oluştu."), type: "error" });
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(staffId: number) {
    setDeletingId(staffId);
    try {
      await api.delete(`/api/staff/delete/${staffId}`);
      setStaff((prev) => prev.filter((s) => s.id !== staffId));
      setToast({ message: "🗑️ Personel silindi.", type: "success" });
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Personel silinirken hata oluştu."), type: "error" });
    } finally {
      setDeletingId(null);
    }
  }

  const inputClass =
    "w-full px-3 py-2 bg-white border border-navy-200 rounded-lg text-slate-900 text-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand/30 focus:border-brand/50 transition-all";

  function ServiceCheckboxes({ selectedIds, onChange }: { selectedIds: number[]; onChange: (ids: number[]) => void }) {
    if (services.length === 0) {
      return <p className="text-xs text-slate-500 italic">Önce Hizmetler sekmesinden hizmet ekleyin.</p>;
    }
    return (
      <div className="flex flex-wrap gap-2">
        {services.map((service) => {
          const checked = selectedIds.includes(service.id);
          return (
            <label
              key={service.id}
              className={`px-3 py-1.5 rounded-lg border text-xs font-medium cursor-pointer transition-all ${
                checked
                  ? "bg-brand/10 border-brand/30 text-brand"
                  : "bg-white border-navy-200 text-slate-500 hover:border-navy-300"
              }`}
            >
              <input
                type="checkbox"
                checked={checked}
                onChange={() => onChange(toggleServiceId(selectedIds, service.id))}
                className="hidden"
              />
              {service.name}
            </label>
          );
        })}
      </div>
    );
  }

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
          <p className="text-red-500 text-lg mb-4">{error}</p>
          <button onClick={fetchAll} className="text-brand hover:text-brand-hover text-sm font-medium cursor-pointer">
            Tekrar Dene
          </button>
        </div>
      )}

      {!loading && !error && (
        <div className="space-y-4">
          {suspended ? null : !showAddForm ? (
            <button
              onClick={() => setShowAddForm(true)}
              className="w-full py-3 text-sm font-medium text-brand border border-dashed border-navy-300 rounded-xl hover:bg-navy-50 hover:border-brand/40 transition-all cursor-pointer"
            >
              + Yeni Personel Ekle
            </button>
          ) : (
            <form
              onSubmit={handleAddSubmit}
              className="bg-navy-50 border border-navy-200 rounded-2xl p-5 space-y-3"
            >
              <input
                type="text"
                required
                placeholder="Personel adı"
                value={addForm.name}
                onChange={(e: ChangeEvent<HTMLInputElement>) => setAddForm({ ...addForm, name: e.target.value })}
                className={inputClass}
              />
              <div>
                <p className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-2">
                  Verdiği Hizmetler
                </p>
                <ServiceCheckboxes
                  selectedIds={addForm.serviceIds}
                  onChange={(ids) => setAddForm({ ...addForm, serviceIds: ids })}
                />
              </div>
              <div className="flex gap-3">
                <button
                  type="submit"
                  disabled={saving}
                  className="flex-1 py-2.5 text-sm font-semibold text-white bg-brand hover:bg-brand-hover rounded-xl disabled:opacity-50 transition-all cursor-pointer"
                >
                  {saving ? "Kaydediliyor..." : "Kaydet"}
                </button>
                <button
                  type="button"
                  onClick={() => { setShowAddForm(false); setAddForm(EMPTY_FORM); }}
                  className="px-4 py-2.5 text-sm font-medium text-slate-500 hover:text-slate-900 border border-navy-200 rounded-xl transition-all cursor-pointer"
                >
                  Vazgeç
                </button>
              </div>
            </form>
          )}

          {staff.length === 0 ? (
            <div className="text-center py-12">
              <p className="text-slate-400 text-sm">Henüz personel eklenmemiş.</p>
            </div>
          ) : (
            staff.map((member) => (
              <div
                key={member.id}
                className="bg-navy-50 border border-navy-100 rounded-2xl p-5"
              >
                {editingId === member.id ? (
                  <form onSubmit={(e) => handleEditSubmit(e, member.id)} className="space-y-3">
                    <input
                      type="text"
                      required
                      value={editForm.name}
                      onChange={(e: ChangeEvent<HTMLInputElement>) => setEditForm({ ...editForm, name: e.target.value })}
                      className={inputClass}
                    />
                    <div>
                      <p className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-2">
                        Verdiği Hizmetler
                      </p>
                      <ServiceCheckboxes
                        selectedIds={editForm.serviceIds}
                        onChange={(ids) => setEditForm({ ...editForm, serviceIds: ids })}
                      />
                    </div>
                    <div className="flex gap-3">
                      <button
                        type="submit"
                        disabled={saving}
                        className="flex-1 py-2 text-sm font-semibold text-white bg-brand hover:bg-brand-hover rounded-lg disabled:opacity-50 cursor-pointer"
                      >
                        {saving ? "Kaydediliyor..." : "Kaydet"}
                      </button>
                      <button
                        type="button"
                        onClick={() => setEditingId(null)}
                        className="px-4 py-2 text-sm font-medium text-slate-500 hover:text-slate-900 border border-navy-200 rounded-lg cursor-pointer"
                      >
                        Vazgeç
                      </button>
                    </div>
                  </form>
                ) : (
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <p className="text-slate-900 font-semibold text-sm">{member.name}</p>
                      <div className="flex flex-wrap gap-1.5 mt-2">
                        {member.services.length === 0 ? (
                          <span className="text-xs text-slate-500 italic">Henüz hizmet atanmamış</span>
                        ) : (
                          member.services.map((s) => (
                            <span
                              key={s.id}
                              className="px-2 py-0.5 bg-brand/10 border border-brand/20 rounded-full text-xs text-brand"
                            >
                              {s.name}
                            </span>
                          ))
                        )}
                      </div>
                    </div>
                    {!suspended && (
                      <div className="flex gap-2 shrink-0">
                        <button
                          onClick={() => startEdit(member)}
                          className="px-3 py-1.5 text-xs font-medium text-slate-600 hover:text-slate-900 border border-navy-200 hover:border-navy-300 rounded-lg transition-all cursor-pointer"
                        >
                          Düzenle
                        </button>
                        <button
                          onClick={() => handleDelete(member.id)}
                          disabled={deletingId === member.id}
                          className="px-3 py-1.5 text-xs font-medium text-red-600 hover:text-red-700 border border-red-200 hover:border-red-300 rounded-lg disabled:opacity-50 transition-all cursor-pointer"
                        >
                          {deletingId === member.id ? "Siliniyor..." : "Sil"}
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
