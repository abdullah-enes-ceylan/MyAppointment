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
// - coverPhotoCardUrl: isletme kapak fotografi yuklemediyse null -- frontend
//   bu durumda gradyan kapagi gostermeye devam eder (bkz. BusinessCard.tsx).
//   Backend gorece bir yol donuyor ("/api/business-photos/xxx-card.jpg"),
//   mutlak hale getirmek icin resolvePhotoUrl kullanilmali (bkz. utils/photo.ts).
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
  coverPhotoCardUrl: string | null;
  // Faz 3.9: sahibi hesap silme talep etmiş mi. GET /api/businesses gibi
  // herkese açık uçlarda hep false (askıdakiler zaten listeden düşüyor) --
  // asıl anlamı GET /api/businesses/my'de: sahip kendi işletmesini görmeye
  // devam ediyor ama panelin salt-okunur banner'ı bu alana bakıyor.
  suspended: boolean;
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
  // Faz 3.9: hesap silme talebi. null = talep yok. Deadline'lar backend'de
  // AccountDeletionProperties'ten HAZIR hesaplanıp geliyor -- burada 30
  // gün/48 saat gibi bir sayı YOK, config değişirse bu dosyanın
  // güncellenmesi gerekmiyor (bkz. UserResponse.java'daki gerekçe).
  deletionRequestedAt: string | null;
  identityAnonymizationDeadlineAt: string | null;
  // Sadece BUSINESS_OWNER + aktif talep varken dolu.
  businessReversalDeadlineAt: string | null;
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

// backend/src/main/java/com/randevu/backend/dto/request/DeleteAccountRequest.java
export interface DeleteAccountRequest {
  password: string;
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

// backend/src/main/java/com/randevu/backend/dto/response/ServiceItemResponse.java
// ServiceItem.java entity'sinde name/description/price/durationInMinutes
// hepsi @Column(nullable=false).
export interface ServiceItemResponse {
  id: number;
  name: string;
  description: string;
  price: number; // BigDecimal -> JSON number
  durationInMinutes: number;
}

// backend/src/main/java/com/randevu/backend/dto/response/BusinessDetailResponse.java
// BusinessResponse'un hizmetler gomulu hali. GET /api/businesses,
// /api/businesses/{id} ve /api/businesses/category/{cat} BUNU donuyor
// (BusinessResponse degil) -- bkz. BusinessController.java:46,61,128.
// coverPhotoDetailUrl BURADA var, BusinessResponse'ta YOK -- sadece detay
// sayfasinda ihtiyac duyulan buyuk boyut, liste gorunumune tasinmiyor.
export interface BusinessDetailResponse extends BusinessResponse {
  serviceItems: ServiceItemResponse[];
  coverPhotoDetailUrl: string | null;
}

// backend/src/main/java/com/randevu/backend/dto/response/NearbyBusinessResponse.java
export interface NearbyBusinessResponse {
  business: BusinessResponse;
  distanceKm: number;
}

// backend/src/main/java/com/randevu/backend/dto/response/ReviewerSummary.java
// User.java entity'sinde name/surName @Column(nullable=false).
export interface ReviewerSummary {
  name: string;
  surName: string;
}

// backend/src/main/java/com/randevu/backend/dto/response/ReviewResponse.java
// Review.java entity'sinde rating/createdAt nullable=false; comment'te
// nullable=false YOK -- entity yorumu acik: "musteri sadece puan verip
// yorum yazmayabilir".
export interface ReviewResponse {
  id: number;
  rating: number;
  comment: string | null;
  createdAt: string; // LocalDateTime -> ISO string
  reviewer: ReviewerSummary;
}

// backend/src/main/java/com/randevu/backend/controller/AppointmentController.java
// (static class AppointmentRequest, satir 93) -- diger tum request DTO'larinin
// aksine dto/request/ altinda DEGIL, dogrudan controller icinde tanimli.
// staffId bilerek @NotNull DEGIL (personel sistemi kullanmayan isletmeler icin).
export interface AppointmentRequest {
  businessId: number;
  serviceId: number;
  appointmentDate: string; // "YYYY-MM-DDTHH:mm:ss"
  staffId?: number;
}

// backend/src/main/java/com/randevu/backend/entity/AppointmentStatus.java
export type AppointmentStatus =
  | "PENDING"
  | "APPROVED"
  | "REJECTED"
  | "CANCELLED"
  | "COMPLETED"
  | "NO_SHOW"
  | "EXPIRED";

// backend/src/main/java/com/randevu/backend/dto/response/BusinessSummary.java
export interface BusinessSummary {
  id: number;
  name: string;
  // Faz 3.9: doluysa geçmiş randevu ekranında işletme adı link DEĞİL düz
  // metin olarak gösterilmeli (askıdaki işletmenin sayfası zaten 404).
  suspended: boolean;
}

// backend/src/main/java/com/randevu/backend/dto/response/DeletionImpactResponse.java
export interface DeletionImpactResponse {
  affectedAppointmentCount: number;
}

// backend/src/main/java/com/randevu/backend/dto/response/CustomerSummary.java
export interface CustomerSummary {
  id: number;
  name: string;
  surName: string;
  phone: string;
}

// backend/src/main/java/com/randevu/backend/dto/response/StaffSummary.java
// Randevu personel atanmadan olusturulmussa null (bkz. AppointmentResponse.staff).
export interface StaffSummary {
  id: number;
  name: string;
}

// backend/src/main/java/com/randevu/backend/dto/response/AppointmentResponse.java
export interface AppointmentResponse {
  id: number;
  appointmentDate: string; // LocalDateTime -> ISO string
  status: AppointmentStatus;
  business: BusinessSummary;
  serviceItem: ServiceItemResponse;
  customer: CustomerSummary;
  staff: StaffSummary | null;
  hasReview: boolean;
  // Sadece PENDING'de dolu, diger her durumda null (bkz. AppointmentService.expiresAt).
  expiresAt: string | null;
}

// backend/src/main/java/com/randevu/backend/dto/request/ReviewRequest.java
export interface ReviewRequest {
  appointmentId: number;
  rating: number;
  comment: string | null;
}

// backend/src/main/java/com/randevu/backend/dto/request/ServiceItemRequest.java
export interface ServiceItemRequest {
  name: string;
  description: string;
  price: number;
  durationInMinutes: number;
}

// backend/src/main/java/com/randevu/backend/dto/response/StaffResponse.java
export interface StaffResponse {
  id: number;
  name: string;
  services: ServiceItemResponse[];
}

// backend/src/main/java/com/randevu/backend/dto/request/StaffRequest.java
export interface StaffRequest {
  name: string;
  serviceIds: number[];
}

// java.time.DayOfWeek -- Jackson varsayilani enum adini oldugu gibi yazar.
export type DayOfWeek =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";

// backend/src/main/java/com/randevu/backend/dto/response/WorkingHourResponse.java
export interface WorkingHourResponse {
  dayOfWeek: DayOfWeek;
  openTime: string | null;
  closeTime: string | null;
  closed: boolean;
}

// backend/src/main/java/com/randevu/backend/dto/request/WorkingHourRequest.java
// openTime/closeTime BILEREK nullable -- closed=true iken ikisi de bos olabilir.
export interface WorkingHourRequest {
  dayOfWeek: DayOfWeek;
  openTime: string | null;
  closeTime: string | null;
  closed: boolean;
}

// backend/src/main/java/com/randevu/backend/dto/response/BusinessClosureResponse.java
// BusinessClosure.java entity'sinde reason'da nullable=false YOK.
export interface BusinessClosureResponse {
  id: number;
  date: string; // LocalDate -> "YYYY-MM-DD"
  reason: string | null;
}

// backend/src/main/java/com/randevu/backend/dto/request/BusinessClosureRequest.java
export interface BusinessClosureRequest {
  date: string;
  reason: string | null;
}

// backend/src/main/java/com/randevu/backend/dto/request/BusinessRequest.java
// phone/description @NotBlank/@NotNull DEGIL (opsiyonel); latitude/longitude
// bilerek @NotNull DEGIL (konum ayri adimda giriliyor, bkz. BusinessRequest.java:51-53).
export interface BusinessRequest {
  name: string;
  address: string;
  phone?: string | null;
  description?: string | null;
  openTime: string;
  closeTime: string;
  category: BusinessCategory;
  servedGender: ServedGender;
  latitude?: number | null;
  longitude?: number | null;
}
