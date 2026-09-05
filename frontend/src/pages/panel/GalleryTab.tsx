import { useState, useEffect, useRef, type ChangeEvent } from "react";
import { ImagePlus, Trash2 } from "lucide-react";
import api from "../../api/axios";
import { getErrorMessage } from "../../api/errors";
import Toast from "../../components/Toast";
import { resolvePhotoUrl } from "../../utils/photo";
import type { BusinessDetailResponse, BusinessResponse } from "../../types/api";

const ACCEPTED_PHOTO_TYPES = "image/jpeg,image/png";

interface ToastState {
  message: string;
  type: "success" | "error";
}

// Isletme fotograflari. Su an backend SADECE tek bir kapak fotografini
// (coverPhotoCardUrl) destekliyor -- gercek coklu galeri (birden fazla
// fotograf, sira/silme) HENUZ YOK. Bu sekme, mockup'taki "Ana Kapak"
// etiketli fotografi (zaten calisan gercek ozellik, eskiden InfoTab'daydi)
// buraya tasiyor; ek galeri slotlari BILEREK "yakinda" olarak isaretli --
// var olmayan bir coklu-yukleme ozelligini calisiyormus gibi gostermek
// yerine acikca bekleyen bir is oldugunu soyluyor (bkz. Navbar'daki
// bildirim zili ile ayni durustluk deseni).
export default function GalleryTab({ businessId, suspended = false }: { businessId: number | null; suspended?: boolean }) {
  const [business, setBusiness] = useState<BusinessDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState<ToastState | null>(null);

  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [photoPreview, setPhotoPreview] = useState<string | null>(null);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [removingPhoto, setRemovingPhoto] = useState(false);
  const [imgFailed, setImgFailed] = useState(false);
  const photoInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (businessId) fetchBusiness();
  }, [businessId]);

  useEffect(() => {
    return () => {
      if (photoPreview) URL.revokeObjectURL(photoPreview);
    };
  }, [photoPreview]);

  async function fetchBusiness() {
    setLoading(true);
    setImgFailed(false);
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
    setImgFailed(false);
  }

  async function handlePhotoUpload() {
    if (!photoFile || !businessId) return;
    setUploadingPhoto(true);
    try {
      const formData = new FormData();
      formData.append("file", photoFile);
      // axios varsayilan Content-Type'i FormData icin YANLIS -- tarayicinin
      // kendi multipart boundary'sini uretebilmesi icin header kaldiriliyor
      // (bkz. eski InfoTab'daki ayni gerekce).
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

  if (loading || !business) {
    return <div className="text-center py-16 text-slate-400 text-sm">Yükleniyor...</div>;
  }

  const coverUrl = photoPreview ?? (imgFailed ? null : resolvePhotoUrl(business.coverPhotoCardUrl));

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-sm">
      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}

      <div className="flex items-center justify-between px-6 py-5 border-b border-slate-100">
        <div>
          <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
            <ImagePlus className="w-5 h-5 text-brand" />
            Galeri &amp; Fotoğraflar
          </h2>
          <p className="text-sm text-slate-500 mt-0.5">Salonunuzun vitrin fotoğrafları ve yapılan uygulamalar</p>
        </div>
        <button
          type="button"
          onClick={() => photoInputRef.current?.click()}
          disabled={suspended}
          className="px-4 py-2 text-sm font-semibold text-white bg-brand hover:bg-brand-hover rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-colors cursor-pointer"
        >
          + Fotoğraf Yükle
        </button>
        <input
          ref={photoInputRef}
          type="file"
          accept={ACCEPTED_PHOTO_TYPES}
          onChange={handlePhotoSelect}
          className="hidden"
        />
      </div>

      <div className="p-6 grid grid-cols-2 sm:grid-cols-3 gap-4">
        {/* Ana Kapak -- gercek, calisan kapak fotografi (bkz. dosya basi
            yorumu). Secilmis ama henuz kaydedilmemis bir dosya varsa onun
            onizlemesi, yoksa kayitli fotograf gosterilir. */}
        <div className="relative aspect-square rounded-xl overflow-hidden border border-slate-200 bg-slate-50 flex items-center justify-center">
          {coverUrl ? (
            <img
              src={coverUrl}
              alt="Ana kapak fotoğrafı"
              onError={() => setImgFailed(true)}
              className="w-full h-full object-cover"
            />
          ) : (
            <span className="text-3xl opacity-30">🖼️</span>
          )}
          <span className="absolute top-2 left-2 px-2 py-0.5 rounded-md bg-black/70 text-white text-[10px] font-semibold">
            Ana Kapak
          </span>
          {business.coverPhotoCardUrl && !photoFile && (
            <button
              type="button"
              onClick={handleRemovePhoto}
              disabled={removingPhoto || suspended}
              title="Kapak fotoğrafını kaldır"
              className="absolute top-2 right-2 p-1.5 rounded-md bg-black/70 text-white hover:bg-red-600 disabled:opacity-50 cursor-pointer transition-colors"
            >
              <Trash2 className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        {/* Coklu galeri -- HENUZ YOK, bkz. dosya basi yorumu. Iki bos slot
            bunu acikca soyluyor, tiklanamaz. */}
        {[0, 1].map((i) => (
          <div
            key={i}
            title="Çoklu galeri yakında eklenecek"
            className="aspect-square rounded-xl border-2 border-dashed border-slate-200 flex items-center justify-center text-slate-300 text-xs font-medium cursor-not-allowed select-none"
          >
            Yakında
          </div>
        ))}
      </div>

      {photoFile && (
        <div className="px-6 pb-6 -mt-2 flex items-center gap-3">
          <button
            type="button"
            onClick={handlePhotoUpload}
            disabled={uploadingPhoto || suspended}
            className="px-4 py-2 text-sm font-semibold text-white bg-brand hover:bg-brand-hover rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-colors cursor-pointer"
          >
            {uploadingPhoto ? "Yükleniyor..." : "Seçilen Fotoğrafı Kaydet"}
          </button>
          <span className="text-xs text-slate-400">JPEG veya PNG, en fazla 5 MB.</span>
        </div>
      )}
    </div>
  );
}
