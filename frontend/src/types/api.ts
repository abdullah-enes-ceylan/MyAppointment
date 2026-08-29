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

// backend/src/main/java/com/randevu/backend/entity/Role.java
export type Role = "USER" | "BUSINESS_OWNER" | "ADMIN";

// backend/src/main/java/com/randevu/backend/dto/response/UserResponse.java
// User.java entity'sinde tum alanlar @Column(nullable=false) -- hicbiri null degil.
export interface UserResponse {
  id: number;
  name: string;
  surName: string;
  email: string;
  phone: string;
  role: Role;
}

// backend/src/main/java/com/randevu/backend/dto/response/ProfileStatsResponse.java
export interface ProfileStatsResponse {
  totalAppointments: number;
  completedAppointments: number;
  upcomingAppointments: number;
  favoriteCount: number;
  reviewCount: number;
}

// backend/src/main/java/com/randevu/backend/dto/response/LoginResponse.java
export interface LoginResponse {
  token: string;
}

// backend/src/main/java/com/randevu/backend/dto/LoginRequest.java
export interface LoginRequest {
  email: string;
  password: string;
}

// backend/src/main/java/com/randevu/backend/dto/request/RegisterRequest.java
export interface RegisterRequest {
  name: string;
  surName: string;
  email: string;
  password: string;
  phone: string;
}

// backend/src/main/java/com/randevu/backend/dto/request/UpdateProfileRequest.java
export interface UpdateProfileRequest {
  name: string;
  surName: string;
  phone: string;
}

// backend/src/main/java/com/randevu/backend/dto/request/ChangePasswordRequest.java
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

// backend/src/main/java/com/randevu/backend/dto/response/ErrorResponse.java
export interface ErrorResponse {
  timestamp: string; // Instant -> ISO string
  status: number;
  error: string;
  message: string;
  path: string;
}

// backend/src/main/java/com/randevu/backend/dto/response/ValidationErrorResponse.java
export interface ValidationErrorResponse extends ErrorResponse {
  fieldErrors: Record<string, string>;
}
