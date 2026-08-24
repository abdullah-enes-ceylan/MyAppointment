import { getCategory, getCategoryLabel, getGenderLabel } from "./CategoryIcons";

// Hizmet grubu rozeti kartın en görünür yerinde (kategori etiketinin
// yanında) çünkü müşterinin "burası bana uygun mu" sorusunu daha kartı
// açmadan cevaplaması gerekiyor -- yanlış yere randevu isteği gönderip
// reddedilmesini önleyen şey bu.
const GENDER_STYLES = {
  MALE: "bg-blue-500/25 text-blue-100",
  FEMALE: "bg-pink-500/25 text-pink-100",
  UNISEX: "bg-white/15 text-white/90",
};

// Küçük bölüm etiketi ("Puan", "Müsaitlik", "Konum") -- tasarımdaki
// bilgi hiyerarşisinin belirleyici parçası: değerin ne olduğunu
// tahmin ettirmek yerine açıkça yazıyor.
function FieldLabel({ children }) {
  return <div className="text-[11px] font-semibold text-slate-700 leading-tight">{children}</div>;
}

// Tüm kartlar aynı boyutta. Bir ara ilk kartı 2 sütun genişliğinde
// "öne çıkan kart" yapmayı denedik ama ızgarada yanındaki sütunun
// altında büyük bir boşluk bırakıyor ve kart oranlarını bozuyordu.
export default function BusinessCard({
  business,
  earliestSlot,
  isFavorited,
  onToggleFavorite,
  onOpen,
}) {
  const { Icon } = getCategory(business.category);

  return (
    <div className="group flex flex-col bg-white border border-slate-200 rounded-2xl shadow-sm hover:shadow-md transition-shadow duration-200 p-3">
      {/* Görsel alanı -- gerçek işletme fotoğrafı henüz bir özellik değil
          (yükleme/depolama yok). Fotoğraf varmış gibi göstermek yerine
          kategoriye göre stilize bir kapak: koyu lacivert gradyan + o
          kategorinin çizgi ikonu. */}
      <div className="relative shrink-0 h-36 rounded-xl overflow-hidden bg-gradient-to-br from-[#161b33] via-[#232c52] to-[#2b3766] flex items-center justify-center">
        <span className="text-white/25 scale-[2.4]">
          <Icon />
        </span>

        <div className="absolute top-2.5 left-2.5 flex items-center gap-1.5">
          <span className="text-[11px] font-medium text-white/90 bg-black/30 backdrop-blur-sm px-2 py-1 rounded-lg">
            {getCategoryLabel(business.category)}
          </span>
          {business.servedGender && (
            <span
              className={`text-[11px] font-medium backdrop-blur-sm px-2 py-1 rounded-lg ${
                GENDER_STYLES[business.servedGender] ?? GENDER_STYLES.UNISEX
              }`}
            >
              {getGenderLabel(business.servedGender)}
            </span>
          )}
        </div>

        {business.distanceKm != null && (
          <span className="absolute bottom-2.5 left-2.5 text-[11px] font-medium text-white/90 bg-black/30 backdrop-blur-sm px-2 py-1 rounded-lg">
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
            className={`absolute top-2.5 right-2.5 w-8 h-8 rounded-full flex items-center justify-center text-sm transition-colors cursor-pointer ${
              isFavorited ? "bg-white" : "bg-black/30 hover:bg-black/50"
            }`}
          >
            {isFavorited ? "❤️" : "🤍"}
          </button>
        )}
      </div>

      {/* İçerik */}
      <div className="flex flex-col flex-1 px-1 pt-3">
        <h3 className="text-base font-bold text-slate-900 flex items-center gap-1.5 min-w-0">
          <span className="truncate">{business.name}</span>
          {business.verified && (
            <span
              title="Onaylı işletme"
              className="shrink-0 inline-flex items-center justify-center w-[18px] h-[18px] rounded-full bg-blue-500 text-white text-[10px] leading-none"
            >
              ✓
            </span>
          )}
        </h3>

        {/* Puan + Müsaitlik yan yana. Müsaitlik rozeti gerçek slot verisi
            bulunduğunda çıkıyor; yoksa sütun tamamen gizleniyor -- boş bir
            "Müsaitlik" başlığı bırakmak yanıltıcı olurdu. */}
        <div className="mt-2 flex items-start gap-3">
          <div className="min-w-0">
            <FieldLabel>Puan</FieldLabel>
            <div className="mt-0.5 text-sm text-slate-600 whitespace-nowrap">
              {business.reviewCount > 0 ? (
                <>
                  <span className="text-amber-400">★</span>{" "}
                  <span className="font-semibold text-slate-900">{business.averageRating.toFixed(1)}</span>{" "}
                  <span className="text-slate-500">({business.reviewCount} yorum)</span>
                </>
              ) : (
                <span className="text-slate-400 italic">Henüz yorum yok</span>
              )}
            </div>
          </div>

          {earliestSlot && (
            <div className="min-w-0">
              <FieldLabel>Müsaitlik</FieldLabel>
              <span className="mt-0.5 inline-flex items-center gap-1 text-[11px] font-medium text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-1 rounded-lg whitespace-nowrap">
                <span className="inline-flex items-center justify-center w-3.5 h-3.5 rounded-full bg-emerald-500 text-white text-[8px] leading-none">
                  ✓
                </span>
                Bugün En Erken: {earliestSlot.slice(0, 5)}
              </span>
            </div>
          )}
        </div>

        {business.address && (
          <div className="mt-2">
            <FieldLabel>Konum</FieldLabel>
            <div className="mt-0.5 flex items-center gap-1 text-sm text-slate-500 min-w-0">
              <span className="shrink-0">📍</span>
              <span className="truncate">{business.address}</span>
            </div>
          </div>
        )}

        {business.description && (
          <p className="mt-2 text-sm text-slate-500 line-clamp-2">{business.description}</p>
        )}

        {/* mt-auto: açıklaması kısa/hiç olmayan kartlarda bile buton en alta
            yapışsın -- ızgaradaki kartların butonları aynı hizada dursun. */}
        <button
          onClick={() => onOpen(business.id)}
          className="mt-auto pt-3 w-full"
        >
          <span className="block w-full py-2.5 text-sm font-semibold text-white bg-[#161b33] hover:bg-[#20264a] rounded-xl transition-colors duration-200 cursor-pointer">
            Randevu Al
          </span>
        </button>
      </div>
    </div>
  );
}
