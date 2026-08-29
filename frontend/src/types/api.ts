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

// backend/src/main/java/com/randevu/backend/dto/response/BusinessResponse.java
// Nullable alanlar backend'deki gercek davranisa gore isaretlendi:
// - phone/description: Business entity'sinde @Column(nullable=false) YOK.
// - openTime/closeTime: entity'de @Column bile yok, JPA varsayilani nullable.
// - averageRating: hic yorum yoksa null (SQL AVG() bos kumede null doner,
//   "puan yok" ile "puan 0" karismasin diye -- bkz. BusinessResponse.java:23-24).
// - latitude/longitude: isletme konumunu henuz girmemisse null.
// - name/address/category/servedGender/verified: entity'de nullable=false.
export interface BusinessResponse {
  id: number;
  name: string;
  address: string;
  phone: string | null;
  description: string | null;
  openTime: string | null; // LocalTime -> "HH:mm:ss"
  closeTime: string | null;
  category: BusinessCategory;
  servedGender: ServedGender;
  averageRating: number | null;
  reviewCount: number;
  latitude: number | null;
  longitude: number | null;
  verified: boolean;
}
