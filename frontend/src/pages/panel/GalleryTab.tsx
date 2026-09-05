import { useState, useEffect, useRef, type ChangeEvent } from "react";
import { ImagePlus, Trash2 } from "lucide-react";
import api from "../../api/axios";
import { getErrorMessage } from "../../api/errors";
import Toast from "../../components/Toast";
import type { BusinessDetailResponse, BusinessPhotoResponse } from "../../types/api";

const ACCEPTED_PHOTO_TYPES = "image/jpeg,image/png";
// Backend'in gercek siniri BusinessPhotoProperties.maxPhotosPerBusiness
// (varsayilan 5) -- burada config'i okuyan bir uc olmadigi icin ayni
// deger elle tekrarlaniyor. Sadece kullanici deneyimi (erken "+Ekle" gizleme)
// icin; gercek sinir backend'de, degisirse burasi da guncellenmeli.
const MAX_PHOTOS = 5;

interface ToastState {
  message: string;
  type: "success" | "error";
}

// İşletme fotoğrafları — V17 çoklu galeri (bkz. ROADMAP 3.17). Sıradaki ilk
// fotoğraf (displayOrder=0) otomatik "Ana Kapak" -- ayrı bir sıralama/sürükle-
// bırak UI'ı BİLEREK yok (kapsam dışı, karar tablosuna bkz.), kapağı
// değiştirmenin tek yolu o fotoğrafı silmek.
export default function GalleryTab({ businessId, suspended = false }: { businessId: number | null; suspended?: boolean }) {
  const [business, setBusiness] = useState<BusinessDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState<ToastState | null>(null);

  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [photoPreview, setPhotoPreview] = useState<string | null>(null);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);

  // Hangi fotografin silindigi/silinme onayi bekledigi -- global degil,
  // fotograf bazinda: bir karttaki islem digerini kilitlemesin.
  const [removingPhotoId, setRemovingPhotoId] = useState<number | null>(null);
  const [confirmingPhotoId, setConfirmingPhotoId] = useState<number | null>(null);
  // Kalici disk/R2 kaybi senaryosunda (bkz. CLAUDE.md karar tablosu) birden
  // fazla fotograf ayni anda kirik olabilir -- tek bir boolean yetmez.
  const [failedPhotoIds, setFailedPhotoIds] = useState<Set<number>>(new Set());

  const photoInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => { if (businessId) fetchBusiness(); }, [businessId]);
  useEffect(() => { return () => { if (photoPreview) URL.revokeObjectURL(photoPreview); }; }, [photoPreview]);

  async function fetchBusiness() {
    setLoading(true);
    setFailedPhotoIds(new Set());
    try {
      const res = await api.get<BusinessDetailResponse>(`/api/businesses/${businessId}`);
      setBusiness(res.data);
    } catch {
      setToast({ message: "İşletme bilgileri yüklenemedi.", type: "error" });
    } finally {
      setLoading(false);
    }
  }

  function handlePhotoSelect(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    if (photoPreview) URL.revokeObjectURL(photoPreview);
    setPhotoFile(file);
    setPhotoPreview(file ? URL.createObjectURL(file) : null);
  }

  function cancelPhotoSelection() {
    if (photoPreview) URL.revokeObjectURL(photoPreview);
    setPhotoFile(null);
    setPhotoPreview(null);
    if (photoInputRef.current) photoInputRef.current.value = "";
  }

  async function handlePhotoUpload() {
    // uploadingPhoto kontrolu buton disabled'ina EK bir savunma -- disabled
    // attribute'unun React render'i yetismeden iki hizli tiklama arasinda
    // ikinci bir POST kacmasin diye (Opus review notu).
    if (!photoFile || !businessId || uploadingPhoto) return;
    setUploadingPhoto(true);
    try {
      const formData = new FormData();
      formData.append("file", photoFile);
      // axios varsayilan Content-Type'i FormData icin YANLIS -- tarayicinin
      // kendi multipart boundary'sini uretebilmesi icin header kaldiriliyor
      // (bkz. eski InfoTab'daki ayni gerekce).
      const res = await api.post<BusinessPhotoResponse[]>(`/api/businesses/${businessId}/photos`, formData, {
        headers: { "Content-Type": undefined },
      });
      setToast({ message: "✅ Fotoğraf eklendi.", type: "success" });
      cancelPhotoSelection();
      setBusiness((prev) => (prev ? { ...prev, photos: res.data } : prev));
    } catch (err) {
      // Backend limit/format/boyut/cozunurluk reddinin HER birinde anlamli
      // bir mesajli BusinessRuleException (409) donuyor, cok buyuk dosyada
      // ise servlet seviyesinde 413 -- getErrorMessage ikisini de oldugu
      // gibi gosterir, "bir seyler ters gitti" DEGIL (Opus review notu).
      setToast({ message: getErrorMessage(err, "Fotoğraf yüklenirken hata oluştu."), type: "error" });
    } finally {
      setUploadingPhoto(false);
    }
  }

  async function handleRemovePhoto(photoId: number) {
    if (!businessId || removingPhotoId !== null) return;
    setRemovingPhotoId(photoId);
    setConfirmingPhotoId(null);
    try {
      const res = await api.delete<BusinessPhotoResponse[]>(`/api/businesses/${businessId}/photos/${photoId}`);
      setToast({ message: "🗑️ Fotoğraf kaldırıldı.", type: "success" });
      setBusiness((prev) => (prev ? { ...prev, photos: res.data } : prev));
      setFailedPhotoIds((prev) => {
        if (!prev.has(photoId)) return prev;
        const next = new Set(prev);
        next.delete(photoId);
        return next;
      });
    } catch (err) {
      setToast({ message: getErrorMessage(err, "Fotoğraf kaldırılırken hata oluştu."), type: "error" });
    } finally {
      setRemovingPhotoId(null);
    }
  }

  function markPhotoFailed(photoId: number) {
    setFailedPhotoIds((prev) => {
      if (prev.has(photoId)) return prev;
      const next = new Set(prev);
      next.add(photoId);
      return next;
    });
  }

  if (loading || !business) {
    return <div className="text-center py-16 text-slate-400 text-sm">Yükleniyor...</div>;
  }

  const photos = business.photos;
  const canAddMore = photos.length < MAX_PHOTOS;

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      <div className="flex items-center justify-between px-6 py-5 border-b border-slate-100">
        <div>
          <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
            <ImagePlus className="w-5 h-5 text-brand" />
            Galeri &amp; Fotoğraflar
          </h2>
          <p className="text-sm text-slate-500 mt-0.5">
            Salonunuzun vitrin fotoğrafları ve yapılan uygulamalar. İlk yüklediğiniz fotoğraf otomatik
            olarak ana kapak olarak kullanılır.
          </p>
        </div>
        <input
          ref={photoInputRef}
          type="file"
          accept={ACCEPTED_PHOTO_TYPES}
          onChange={handlePhotoSelect}
          className="hidden"
        />
      </div>

      <div className="p-6 grid grid-cols-2 sm:grid-cols-3 gap-4">
        {photos.map((photo, index) => {
          const failed = failedPhotoIds.has(photo.id);
          const isRemoving = removingPhotoId === photo.id;
          const isConfirming = confirmingPhotoId === photo.id;
          return (
            <div
              key={photo.id}
              className="relative aspect-square rounded-xl overflow-hidden border border-slate-200 bg-slate-50 flex items-center justify-center"
            >
              {!failed ? (
                <img
                  src={photo.cardUrl}
                  alt={index === 0 ? "Ana kapak fotoğrafı" : `Fotoğraf ${index + 1}`}
                  onError={() => markPhotoFailed(photo.id)}
                  className="w-full h-full object-cover"
                />
              ) : (
                <span className="text-3xl opacity-30">🖼️</span>
              )}

              {index === 0 && (
                <span className="absolute top-2 left-2 px-2 py-0.5 rounded-md bg-black/70 text-white text-[10px] font-semibold">
                  Ana Kapak
                </span>
              )}

              {!suspended && (
                isConfirming ? (
                  <div className="absolute inset-0 bg-black/70 flex flex-col items-center justify-center gap-2 p-2">
                    <span className="text-white text-xs text-center">Silinsin mi?</span>
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={() => handleRemovePhoto(photo.id)}
                        disabled={isRemoving}
                        className="px-2.5 py-1 text-xs font-semibold text-white bg-red-600 hover:bg-red-500 rounded-md disabled:opacity-50 cursor-pointer transition-colors"
                      >
                        {isRemoving ? "Siliniyor..." : "Evet, sil"}
                      </button>
                      <button
                        type="button"
                        onClick={() => setConfirmingPhotoId(null)}
                        disabled={isRemoving}
                        className="px-2.5 py-1 text-xs font-medium text-white/80 hover:text-white border border-white/30 rounded-md disabled:opacity-50 cursor-pointer transition-colors"
                      >
                        Vazgeç
                      </button>
                    </div>
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={() => setConfirmingPhotoId(photo.id)}
                    disabled={removingPhotoId !== null}
                    title="Fotoğrafı kaldır"
                    className="absolute top-2 right-2 p-1.5 rounded-md bg-black/70 text-white hover:bg-red-600 disabled:opacity-50 cursor-pointer transition-colors"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                )
              )}
            </div>
          );
        })}

        {!suspended && canAddMore && (
          <div className="relative aspect-square rounded-xl overflow-hidden border-2 border-dashed border-slate-200 flex items-center justify-center">
            {photoPreview ? (
              <>
                <img src={photoPreview} alt="Yüklenecek fotoğraf önizlemesi" className="w-full h-full object-cover" />
                <div className="absolute inset-0 bg-black/50 flex flex-col items-center justify-center gap-2 p-2">
                  <button
                    type="button"
                    onClick={handlePhotoUpload}
                    disabled={uploadingPhoto}
                    className="px-3 py-1.5 text-xs font-semibold text-white bg-brand hover:bg-brand-hover rounded-lg disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer transition-colors"
                  >
                    {uploadingPhoto ? "Yükleniyor..." : "Kaydet"}
                  </button>
                  <button
                    type="button"
                    onClick={cancelPhotoSelection}
                    disabled={uploadingPhoto}
                    className="px-3 py-1.5 text-xs font-medium text-white/80 hover:text-white border border-white/30 rounded-lg disabled:opacity-50 cursor-pointer transition-colors"
                  >
                    Vazgeç
                  </button>
                </div>
              </>
            ) : (
              <button
                type="button"
                onClick={() => photoInputRef.current?.click()}
                title="Fotoğraf ekle"
                className="w-full h-full flex flex-col items-center justify-center gap-1 text-slate-300 hover:text-brand hover:border-brand/40 transition-colors cursor-pointer"
              >
                <ImagePlus className="w-6 h-6" />
                <span className="text-xs font-medium">Ekle</span>
              </button>
            )}
          </div>
        )}
      </div>

      <div className="px-6 pb-6 -mt-2 flex items-center justify-between">
        <span className="text-xs text-slate-400">JPEG veya PNG, en fazla 5 MB.</span>
        {!canAddMore && <span className="text-xs text-slate-400">En fazla {MAX_PHOTOS} fotoğraf yükleyebilirsiniz.</span>}
      </div>
    </div>
  );
}
