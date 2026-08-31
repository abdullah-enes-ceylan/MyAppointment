import { useState } from "react";
import { getCategory, getCategoryLabel, getGenderLabel } from "./CategoryIcons";
import { resolvePhotoUrl } from "../utils/photo";
import type { BusinessResponse, ServedGender } from "../types/api";

// Hizmet grubu rozeti kartın en görünür yerinde (kategori etiketinin
// yanında) çünkü müşterinin "burası bana uygun mu" sorusunu daha kartı
// açmadan cevaplaması gerekiyor -- yanlış yere randevu isteği gönderip
// reddedilmesini önleyen şey bu.
const GENDER_STYLES: Record<ServedGender, string> = {
  MALE: "bg-blue-500/25 text-blue-100",
  FEMALE: "bg-pink-500/25 text-pink-100",
  UNISEX: "bg-white/15 text-white/90",
};

// Prototip tasarımdaki (FavoritesDrawer.tsx) aynı kalp path'i -- tutarlılık
// için birebir kopyalandı. Emoji yerine SVG: fill/stroke currentColor'a
// bağlı olduğu için favori durumuna göre renk (accent) CSS'ten kontrol
// edilebiliyor, emoji ile bu mümkün değildi.
function HeartIcon({ filled }: { filled: boolean }) {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill={filled ? "currentColor" : "none"}
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M12 20.3l-1.4-1.3C5.4 14.4 2 11.3 2 7.6 2 4.9 4.1 3 6.6 3c1.6 0 3.1.8 4 2 .9-1.2 2.4-2 4-2C17.9 3 20 4.9 20 7.6c0 3.7-3.4 6.8-8.6 11.4z" />
    </svg>
  );
}

// business: uc farkli uctan gelen, ortusen ama farkli sekiller kabul ediyor
// (GET /api/businesses -> BusinessResponse, GET /api/favorites/me ->
// BusinessDetailResponse'un fazladan serviceItems'i, nearby modunda
// HomePage'in distanceKm'i duz nesnenin ustune yaydigi hali) -- BusinessResponse
// bu ucunun ortak alt kumesi, distanceKm ise sadece nearby'de var oldugu icin
// opsiyonel. Diger iki sekil BusinessResponse'a yapisal olarak uyuyor (fazladan
// alan tasimasi TS'te sorun degil).
interface BusinessCardProps {
  business: BusinessResponse & { distanceKm?: number };
  earliestSlot?: string;
  isFavorited?: boolean;
  onToggleFavorite?: (businessId: number) => void;
  onOpen: (businessId: number) => void;
}

// Tüm kartlar aynı boyutta. Bir ara ilk kartı 2 sütun genişliğinde
// "öne çıkan kart" yapmayı denedik ama ızgarada yanındaki sütunun
// altında büyük bir boşluk bırakıyor ve kart oranlarını bozuyordu.
//
// Google AI Studio prototipiyle karşılaştırma sonrası (2026-08-30) kapak
// gorseli kart kenarlarina tasip (bleed) tam genislikte oldu, kart geneli
// tiklanabilir hale geldi (eskiden sadece "Randevu Al" butonu). "Puan",
// "Muhsaitlik" gibi ayri etiketli bloklar yerine basliginin yanina
// kompakt bir puan rozeti geldi -- prototipteki SalonCard'in bilgi
// hiyerarsisiyle ayni.
export default function BusinessCard({
  business,
  earliestSlot,
  isFavorited,
  onToggleFavorite,
  onOpen,
}: BusinessCardProps) {
  const { Icon } = getCategory(business.category);
  const coverUrl = resolvePhotoUrl(business.coverPhotoCardUrl);
  // Kalici olmayan disk senaryosunda (bkz. CLAUDE.md karar tablosu) DB'de
  // photo_key dururken dosya diskten gidebilir -- servis ucu bu durumda 404
  // doner. onError olmadan tarayici <img>'i DOM'da tutup kirik resim ikonu
  // gosterir; bu bayrak sayesinde ayni "fotografsiz" gradyan+ikon kapagina
  // duseriz, kirik ikon hic gorunmez.
  const [imgFailed, setImgFailed] = useState(false);
  const showPhoto = coverUrl && !imgFailed;
  const hasRating = business.reviewCount > 0 && business.averageRating != null;

  return (
    <div
      onClick={() => onOpen(business.id)}
      className="group flex flex-col bg-white rounded-2xl border border-slate-200 hover:border-slate-300 shadow-[0_4px_20px_rgba(15,23,42,0.04)] hover:shadow-[0_12px_30px_rgba(10,30,66,0.12)] transition-all duration-300 overflow-hidden cursor-pointer transform hover:-translate-y-1"
    >
      {/* Görsel alanı -- kart kenarına tam bleed (prototipteki gibi), 16:10
          oranlı. İşletme kapak fotoğrafı yüklediyse onu gösterir, yüklemediyse
          VEYA yükleme başarısız olduysa (coverUrl null ya da imgFailed)
          kategoriye göre stilize kapağa (marka gradyanı + o kategorinin çizgi
          ikonu) düşer. aspect-[16/10] sabit oran, fotoğraf gec/hic yüklenmese
          bile alanı ANINDA rezerve eder -- eski sabit h-36 ile aynı CLS
          güvenliği, sadece orantısal (kart genişliğine göre ölçekleniyor). */}
      <div className="relative shrink-0 aspect-[16/10] w-full overflow-hidden bg-gradient-to-br from-brand via-brand-mid to-brand-glow flex items-center justify-center">
        {showPhoto ? (
          <img
            src={coverUrl}
            alt={business.name}
            loading="lazy"
            decoding="async"
            onError={() => setImgFailed(true)}
            className="absolute inset-0 w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
          />
        ) : (
          <span className="text-white/25 scale-[2.4]">
            <Icon />
          </span>
        )}

        <div className="absolute top-1.5 left-1.5 flex items-center gap-1 max-w-[calc(100%-2.75rem)]">
          <span className="text-[10px] font-medium text-white/90 bg-black/30 backdrop-blur-sm px-1.5 py-0.5 rounded-md truncate">
            {getCategoryLabel(business.category)}
          </span>
          {business.servedGender && (
            <span
              className={`text-[10px] font-medium backdrop-blur-sm px-1.5 py-0.5 rounded-md shrink-0 ${
                GENDER_STYLES[business.servedGender] ?? GENDER_STYLES.UNISEX
              }`}
            >
              {getGenderLabel(business.servedGender)}
            </span>
          )}
        </div>

        {business.distanceKm != null && (
          <span className="absolute bottom-1.5 left-1.5 text-[10px] font-medium text-white/90 bg-black/30 backdrop-blur-sm px-1.5 py-0.5 rounded-md">
            {business.distanceKm.toFixed(1)} km
          </span>
        )}

        {onToggleFavorite && (
          <button
            onClick={(e) => {
              e.stopPropagation();
              onToggleFavorite(business.id);
            }}
            title={isFavorited ? "Favorilerden çıkar" : "Favorilere ekle"}
            className={`absolute top-1.5 right-1.5 w-6.5 h-6.5 rounded-full backdrop-blur-sm flex items-center justify-center transition-all duration-200 cursor-pointer ${
              isFavorited ? "bg-white text-accent scale-110" : "bg-black/30 hover:bg-white/90 text-white hover:text-accent"
            }`}
          >
            <HeartIcon filled={!!isFavorited} />
          </button>
        )}
      </div>

      {/* İçerik -- 4'lü sıra grid'ine sığması için kompakt (2026-08-31):
          eskiden p-4/text-lg/py-2.5 idi, dar sütunda taşıyordu. Açıklama
          satırı bilerek kaldırıldı -- bu genişlikte 2 satır bile kartı
          gereksiz uzatıyordu, başlık+adres+CTA yeterli bilgiyi veriyor. */}
      <div className="flex flex-col flex-1 p-2.5 sm:p-3">
        {/* Başlık + kompakt puan rozeti yan yana -- prototipteki SalonCard
            deseni. Puan yoksa rozet yerine sağda küçük bir "Henüz yorum
            yok" metni kalıyor, satır boş görünmesin diye. */}
        <div className="flex items-start justify-between gap-1.5">
          <h3 className="font-bold text-slate-900 text-xs sm:text-sm group-hover:text-brand transition-colors line-clamp-1 flex items-center gap-1 min-w-0">
            <span className="truncate">{business.name}</span>
            {business.verified && (
              <span
                title="Onaylı işletme"
                className="shrink-0 inline-flex items-center justify-center w-3.5 h-3.5 rounded-full bg-blue-500 text-white text-[8px] leading-none"
              >
                ✓
              </span>
            )}
          </h3>

          {/* averageRating null kontrolü BİLEREK reviewCount>0 kontrolüne
              EKLENDİ (backend sözleşmesi ikisinin hep birlikte doğru olacağını
              garanti ediyor, ama TS bunu tek başına reviewCount'tan çıkaramaz
              -- bu ek kontrol olmadan averageRating "null olabilir" kalırdı). */}
          {hasRating ? (
            <div className="flex items-center gap-0.5 bg-canvas-soft text-brand px-1.5 py-0.5 rounded-md text-[10px] font-bold shrink-0">
              <span className="text-amber-400">★</span>
              <span>{business.averageRating!.toFixed(1)}</span>
            </div>
          ) : (
            <span className="shrink-0 text-[9px] text-slate-400 italic whitespace-nowrap">Yeni</span>
          )}
        </div>

        {business.address && (
          <div className="mt-1 flex items-center gap-1 text-[11px] text-slate-500 min-w-0">
            <span className="shrink-0">📍</span>
            <span className="truncate">{business.address}</span>
          </div>
        )}

        {/* Müsaitlik rozeti gerçek slot verisi bulunduğunda çıkıyor; yoksa
            hiç gösterilmiyor -- uydurma saat göstermiyoruz. */}
        {earliestSlot && (
          <span className="mt-1.5 inline-flex items-center gap-1 self-start text-[9px] font-medium text-emerald-700 bg-emerald-50 border border-emerald-200 px-1.5 py-0.5 rounded-md whitespace-nowrap">
            <span className="inline-flex items-center justify-center w-3 h-3 rounded-full bg-emerald-500 text-white text-[7px] leading-none">
              ✓
            </span>
            {earliestSlot.slice(0, 5)}
          </span>
        )}

        {/* mt-auto: açıklaması kısa/hiç olmayan kartlarda bile buton en alta
            yapışsın -- ızgaradaki kartların butonları aynı hizada dursun.
            stopPropagation: kart geneli zaten aynı yere (onOpen) götürüyor,
            bu olmadan tıklama önce buton onClick'ini sonra kartın kendi
            onClick'ini de tetikler (aynı yere iki kez navigate -- zararsız
            ama temiz değil). */}
        <div className="mt-auto pt-2 border-t border-slate-100">
          <button
            onClick={(e) => {
              e.stopPropagation();
              onOpen(business.id);
            }}
            className="w-full py-1.5 text-xs font-semibold text-white bg-brand hover:bg-brand-hover rounded-lg transition-colors duration-200 cursor-pointer"
          >
            Randevu Al
          </button>
        </div>
      </div>
    </div>
  );
}
