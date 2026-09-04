import type { ComponentType } from "react";
import { Brush, Droplet, Feather, Hand, LayoutGrid, Scissors, Sparkles } from "lucide-react";
import type { BusinessCategory, ServedGender } from "../types/api";

// Kategori ikonlari -- 2. Google AI Studio prototipiyle karsilastirma
// sonrasi (2026-09-04) elle cizilmis SVG'lerden lucide-react'e gecildi.
// Ayni gerekce hala gecerli: emoji degil cizgi ikon (isletim sistemleri
// arasi tutarlilik icin), sadece kutuphane degisti -- kendi SVG'lerimizi
// elle bakim yapmak yerine hazir, tutarli bir set.
const ICON_SIZE = 20;

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
  { key: "ALL", label: "Tümü", Icon: () => <LayoutGrid size={ICON_SIZE} /> },
  { key: "HAIRDRESSER", label: "Kuaför", Icon: () => <Scissors size={ICON_SIZE} /> },
  { key: "BEAUTY_SALON", label: "Güzellik", Icon: () => <Sparkles size={ICON_SIZE} /> },
  { key: "SPA_WELLNESS", label: "Spa", Icon: () => <Droplet size={ICON_SIZE} /> },
  { key: "NAIL_STUDIO", label: "Tırnak", Icon: () => <Hand size={ICON_SIZE} /> },
  { key: "MAKEUP_STUDIO", label: "Makyaj", Icon: () => <Brush size={ICON_SIZE} /> },
  { key: "TATTOO_STUDIO", label: "Dövme", Icon: () => <Feather size={ICON_SIZE} /> },
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
