// Backend enum'larinin TS union karsiligi. Degerler backend/entity altindaki
// enum dosyalariyla birebir ayni tutulur -- yeni bir deger eklenirse burasi
// da guncellenir, aksi halde o deger union'a uymadigi icin derleme hatasi
// alinir (bu KASITLI: sessizce gozden kacmasindansa hata versin).
//
// Bu dosya, DTO tipleri her PR'da ilgili dosyayla birlikte eklenecek sekilde
// buyuyecek (kademeli TS gecis plani) -- su an sadece CategoryIcons.tsx'in
// ihtiyaci olan iki enum var.

// backend/src/main/java/com/randevu/backend/entity/BusinessCategory.java
export type BusinessCategory =
  | "HAIRDRESSER"
  | "BEAUTY_SALON"
  | "SPA_WELLNESS"
  | "NAIL_STUDIO"
  | "MAKEUP_STUDIO"
  | "TATTOO_STUDIO";

// backend/src/main/java/com/randevu/backend/entity/ServedGender.java
export type ServedGender = "MALE" | "FEMALE" | "UNISEX";
