import type { ComponentType, SVGProps } from "react";
import type { BusinessCategory, ServedGender } from "../types/api";

// Kategori sekmelerindeki çizgi ikonlar. Emoji yerine SVG: sekme çubuğu
// tasarımın en görünür parçası ve emoji her işletim sisteminde farklı
// (Windows'ta düz, iOS'ta 3B renkli) görünüyor -- SVG her yerde aynı
// duruyor ve currentColor sayesinde aktif/pasif renginden otomatik
// etkileniyor. Emoji, kart içeriğinde (⭐ 📍) kalmaya devam ediyor.
const base: SVGProps<SVGSVGElement> = {
  width: 20,
  height: 20,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.7,
  strokeLinecap: "round",
  strokeLinejoin: "round",
};

const GridIcon = () => (
  <svg {...base}>
    <rect x="3" y="3" width="7" height="7" rx="1.5" />
    <rect x="14" y="3" width="7" height="7" rx="1.5" />
    <rect x="3" y="14" width="7" height="7" rx="1.5" />
    <rect x="14" y="14" width="7" height="7" rx="1.5" />
  </svg>
);

const ScissorsIcon = () => (
  <svg {...base}>
    <circle cx="6" cy="6" r="2.6" />
    <circle cx="6" cy="18" r="2.6" />
    <line x1="20" y1="4" x2="8.1" y2="15.9" />
    <line x1="14.5" y1="14.5" x2="20" y2="20" />
    <line x1="8.1" y1="8.1" x2="12" y2="12" />
  </svg>
);

const SparkleIcon = () => (
  <svg {...base}>
    <path d="M12 3l1.9 5.6L19.5 10l-5.6 1.4L12 17l-1.9-5.6L4.5 10l5.6-1.4z" />
    <path d="M18.5 16.5l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z" />
  </svg>
);

const DropletIcon = () => (
  <svg {...base}>
    <path d="M12 3.2l4.7 6.2a6 6 0 1 1-9.4 0z" />
  </svg>
);

const NailIcon = () => (
  <svg {...base}>
    <path d="M8.5 3.5h7V14a3.5 3.5 0 0 1-7 0z" />
    <path d="M8.5 18.5h7" />
  </svg>
);

const BrushIcon = () => (
  <svg {...base}>
    <path d="M9 12l8.1-8.1a2.7 2.7 0 1 1 3.8 3.8L12.8 15.8" />
    <path d="M7 15c-1.7 0-3 1.4-3 3 0 1.3-2.4 1.5-2 2 1.1 1.1 2.5 2 4 2 2.2 0 4-1.8 4-4 0-1.7-1.4-3-3-3z" />
  </svg>
);

const PenIcon = () => (
  <svg {...base}>
    <path d="M12.5 18.5L18 13l3 3-5.5 5.5z" />
    <path d="M18 13l-1.4-7.6L3 2l3.4 13.6L14 17z" />
    <path d="M3 2l7.4 7.4" />
    <circle cx="11.7" cy="11.7" r="1.9" />
  </svg>
);

interface CategoryOption {
  key: BusinessCategory | "ALL";
  label: string;
  Icon: ComponentType;
}

// Kategori listesi TEK KAYNAK burada -- eskiden HomePage ve BusinessCard'da
// ayrı ayrı tanımlıydı (biri label+icon, diğeri sadece icon), ikisi de aynı
// enum'ı tekrarlıyordu. Backend'deki BusinessCategory enum'ıyla birebir.
//
// "Berber" ayrı bir kategori DEĞİL: berber de erkek kuaförü de Kuaför
// kategorisinde, aradaki fark GENDERS ile ifade ediliyor (bkz. backend
// ServedGender enum'ındaki gerekçe).
export const CATEGORIES: CategoryOption[] = [
  { key: "ALL", label: "Tümü", Icon: GridIcon },
  { key: "HAIRDRESSER", label: "Kuaför", Icon: ScissorsIcon },
  { key: "BEAUTY_SALON", label: "Güzellik", Icon: SparkleIcon },
  { key: "SPA_WELLNESS", label: "Spa", Icon: DropletIcon },
  { key: "NAIL_STUDIO", label: "Tırnak", Icon: NailIcon },
  { key: "MAKEUP_STUDIO", label: "Makyaj", Icon: BrushIcon },
  { key: "TATTOO_STUDIO", label: "Dövme", Icon: PenIcon },
];

interface GenderOption {
  key: ServedGender | "ALL";
  label: string;
}

// Backend ServedGender enum'ıyla birebir. "ALL" sadece filtrede kullanılan
// bir arayüz değeri -- backend'de karşılığı yok, "filtreleme yapma" demek.
export const GENDERS: GenderOption[] = [
  { key: "ALL", label: "Herkes" },
  { key: "MALE", label: "Erkek" },
  { key: "FEMALE", label: "Kadın" },
  { key: "UNISEX", label: "Unisex" },
];

export function getCategory(key: BusinessCategory | "ALL"): CategoryOption {
  return CATEGORIES.find((c) => c.key === key) ?? CATEGORIES[0];
}

export function getCategoryLabel(key: BusinessCategory | "ALL"): string {
  return getCategory(key).label;
}

export function getGenderLabel(key: ServedGender | "ALL"): string {
  return GENDERS.find((g) => g.key === key)?.label ?? key;
}
