import { useState } from "react";
import { BadgeCheck, Clock, Heart, MapPin, Star } from "lucide-react";
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

// 2. Google AI Studio prototipiyle karşılaştırma sonrası (2026-09-04):
// kapak görseli üstüne koyu navy gradyan + puan/müsaitlik rozetleri
// görselin İÇİNE taşındı (eskiden görsel altında ayrı bir bilgi satırıydı).
// AI Studio'daki "3 tıklanabilir hızlı saat" özelliği BİLEREK taşınmadı --
// bu, her kart için 3 ayrı müsaitlik isteği demek olurdu (şu an zaten TEK
// slot için işletme başına 1 istek atılıyor ve bu bile ana sayfada rate
// limit'e takılabiliyor, bkz. bilinen açık iş); tek "Bugün En Erken"
// rozeti + "Randevu Al" butonu kalıyor. ₺ (priceLevel) rozeti de BİLEREK
// yok -- backend'de fiyat seviyesi kavramı yok, uydurma sembol yanıltıcı
// olurdu. Tagline slotu gerçek `business.description` ile dolduruluyor
// (AI Studio'daki ayrı "tagline" alanının backend karşılığımız yok, ama
// description kavramsal olarak aynı işi görüyor).
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
      className="group flex flex-col bg-white rounded-2xl border border-slate-200/90 hover:border-slate-300 shadow-xs hover:shadow-md transition-all duration-300 overflow-hidden cursor-pointer"
    >
      {/* Görsel alanı -- kart kenarına tam bleed, 16:10 oranlı. İşletme
          kapak fotoğrafı yüklediyse onu gösterir, yüklemediyse VEYA yükleme
          başarısız olduysa (coverUrl null ya da imgFailed) kategoriye göre
          stilize kapağa (marka gradyanı + o kategorinin çizgi ikonu) düşer. */}
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

        {/* Koyu navy gradyan -- alttaki rozetlerin okunurluğu için (AI
            Studio'daki BusinessCard ile aynı yaklaşım). */}
        <div className="absolute inset-0 bg-gradient-to-t from-[#0a1726]/70 via-transparent to-[#0a1726]/10 pointer-events-none" />

        <div className="absolute top-2 left-2 right-2 flex items-start justify-between gap-1">
          <div className="flex items-center gap-1 flex-wrap min-w-0">
            <span className="text-[10px] font-bold text-white bg-[#0d2238]/90 backdrop-blur-sm px-2 py-0.5 rounded-full uppercase tracking-wide truncate">
              {getCategoryLabel(business.category)}
            </span>
            {business.servedGender && (
              <span
                className={`text-[10px] font-semibold backdrop-blur-sm px-2 py-0.5 rounded-full shrink-0 ${
                  GENDER_STYLES[business.servedGender] ?? GENDER_STYLES.UNISEX
                }`}
              >
                {getGenderLabel(business.servedGender)}
              </span>
            )}
          </div>

          {onToggleFavorite && (
            <button
              onClick={(e) => {
                e.stopPropagation();
                onToggleFavorite(business.id);
              }}
              title={isFavorited ? "Favorilerden çıkar" : "Favorilere ekle"}
              className={`w-7 h-7 rounded-full backdrop-blur-sm flex items-center justify-center transition-all duration-200 cursor-pointer shrink-0 ${
                isFavorited ? "bg-white text-rose-500" : "bg-black/30 hover:bg-white/90 text-white hover:text-rose-500"
              }`}
            >
              <Heart className="w-3.5 h-3.5" fill={isFavorited ? "currentColor" : "none"} />
            </button>
          )}
        </div>

        {/* Alt rozetler -- puan (ya da yeniyse "Yeni") solda, mesafe/müsaitlik
            sağda. Uydurma veri yok: puan sadece gerçek yorum varsa, müsaitlik
            sadece gerçek slot verisi bulunduğunda çıkıyor. */}
        <div className="absolute bottom-2 left-2 right-2 flex items-center justify-between text-white text-xs">
          {hasRating ? (
            <div className="flex items-center gap-1 bg-[#0a1a2c]/85 backdrop-blur-sm px-2 py-0.5 rounded-full font-bold border border-white/10">
              <Star className="w-3 h-3 text-amber-400 fill-amber-400" />
              <span>{business.averageRating!.toFixed(1)}</span>
              <span className="text-slate-300 font-normal text-[10px]">({business.reviewCount})</span>
            </div>
          ) : (
            <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-[#0a1a2c]/85 backdrop-blur-sm border border-white/10 italic">
              Yeni
            </span>
          )}

          {business.distanceKm != null ? (
            <span className="text-[10px] font-medium px-2 py-0.5 rounded-full bg-black/40 backdrop-blur-sm">
              {business.distanceKm.toFixed(1)} km
            </span>
          ) : (
            earliestSlot && (
              <span className="text-[10px] font-medium px-2 py-0.5 rounded-full bg-sky-600/90 backdrop-blur-sm">
                Bugün Uygun
              </span>
            )
          )}
        </div>
      </div>

      {/* İçerik */}
      <div className="flex flex-col flex-1 p-3 sm:p-4">
        <div className="flex items-start justify-between gap-1.5">
          <h3 className="font-bold text-slate-900 text-sm group-hover:text-brand transition-colors line-clamp-1 flex items-center gap-1 min-w-0">
            <span className="truncate">{business.name}</span>
            {business.verified && <BadgeCheck className="w-4 h-4 text-sky-600 shrink-0" aria-label="Onaylı işletme" />}
          </h3>
        </div>

        {business.address && (
          <div className="mt-1 flex items-center gap-1 text-[11px] text-slate-500 min-w-0">
            <MapPin className="w-3 h-3 text-slate-400 shrink-0" />
            <span className="truncate">{business.address}</span>
          </div>
        )}

        {business.description && (
          <p className="text-xs text-slate-600 mt-1.5 line-clamp-1">{business.description}</p>
        )}

        {/* mt-auto: açıklaması kısa/hiç olmayan kartlarda bile buton en alta
            yapışsın. stopPropagation: kart geneli zaten aynı yere (onOpen)
            götürüyor, bu olmadan tıklama önce buton onClick'ini sonra kartın
            kendi onClick'ini de tetikler. */}
        <div className="mt-3 pt-2.5 border-t border-slate-100">
          {earliestSlot && (
            <div className="flex items-center justify-between mb-2">
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1">
                <Clock className="w-3 h-3 text-brand" />
                Bugün En Erken: {earliestSlot.slice(0, 5)}
              </span>
            </div>
          )}
          <button
            onClick={(e) => {
              e.stopPropagation();
              onOpen(business.id);
            }}
            className="w-full py-2 text-xs sm:text-sm font-semibold text-white bg-brand hover:bg-brand-hover rounded-xl transition-colors duration-200 cursor-pointer"
          >
            Randevu Al
          </button>
        </div>
      </div>
    </div>
  );
}
