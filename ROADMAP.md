# Randevum — Yol Haritası ve Durum Tespiti

## Context

Randevum, randevuyla çalışan yerel işletmeler (berber, kuaför, güzellik salonu, spa, dövme) için
çok kiracılı (multi-tenant) bir SaaS randevu platformu. Şu an tek işletme için uçtan uca akış
çalışıyor: kayıt → JWT login → kategori filtresi → hizmet seçimi → boş slot hesaplama →
randevu talebi (PENDING) → işletme inbox'ında onay/ret.

**Neden bu plan:** Kod tabanı okunduğunda çekirdek iş mantığı sağlam (interval overlap algoritması
doğru, katmanlı mimari kurulmuş, JWT authentication çalışıyor) ancak **authorization katmanı
neredeyse hiç yok**. Giriş yapmış herhangi bir kullanıcı, `businessId` parametresini değiştirerek
başka bir işletmenin müşteri listesini, telefon numaralarını ve randevu geçmişini okuyabiliyor;
hizmet fiyatlarını değiştirebiliyor; kimlik doğrulaması olmadan kendini ADMIN yapabiliyor.

Ürün 1-2 ay içinde gerçek işletmelerde ücretsiz beta'ya girecek ve gerçek müşteri kişisel verisi
(ad, telefon, randevu geçmişi) işleyecek. Bu nedenle **güvenlik ve multi-tenant izolasyonu, yeni
özelliklerden önce** gelmek zorunda. Ayrıca beta'dan sonra şema göçü gerçek veri üzerinde
yapılacağı için, veri modeli kararlarının (personel/kapasite, çalışma saatleri, para tipi)
beta öncesinde doğru verilmesi gerekiyor.

**Hedeflenen sonuç:** Canlıya alınabilir, kiracılar arası veri sızıntısı olmayan, yorum/puan
sistemi kural olarak garanti altına alınmış, izlenebilir ve yedeklenen bir SaaS.

---

## Kabul Edilen Kararlar

| Konu | Karar | Sonucu |
|---|---|---|
| Tenant modeli | Bir sahip **N işletme** yönetebilir | `businessId` asla JWT'ye gömülmez; her istekte sahiplik DB'den doğrulanır. Panelde işletme seçici gerekir. |
| Personel/kapasite | **Faz 2'de tam `Staff` modeli** | Faz 1 şeması buna göre planlanır: `WorkingHour` tablosu baştan ayrı entity olur, Faz 2'de nullable `staff_id` eklenir. |
| Bildirim kanalı | **Karar ertelendi** | Faz 3'te kanal-bağımsız `NotificationPort` soyutlaması + in-app/log adapter kurulur. E-posta/SMS/WhatsApp adapter'ı karar verildiğinde tek sınıf eklenerek devreye girer. |
| Para tipi | `double` → **`BigDecimal`** | Faz 1'de migration ile. |
| Zaman | `LocalDateTime` + sabit `Europe/Istanbul` | Tek ülke kapsamında yeterli; sunucu TZ'si açıkça UTC'ye sabitlenir, dönüşüm uygulama katmanında. |

---

## Çalışma Kuralı — Kim Yazacak

| Konu | Kim yazar | Neden |
|---|---|---|
| Global exception handling, method security, sahiplik kontrolleri, slot algoritması refactor'ü, Staff modeli, Review garantisi, Flyway | **Sen** | Bu projede ilk kez karşılaşılan konseptler. Struggle işin özü. AI yapıyı ve nedenini anlatır. |
| DTO'lar, mapper'lar, tekrar eden CRUD controller'ları, seeder güncellemeleri, Tailwind ekranları | **AI** | Bir kez anlaşılıp uygulanmış, tekrar eden işler. Her satır okunur, anlaşılmayan satırda durulur. |

---

# FAZ 0 — Güvenlik Acil Müdahale

**Hedef:** Bugün sömürülebilir durumdaki zafiyetleri kapatmak.
**Neden ilk:** Bu açıklar var oldukça üstüne yazılan her özellik teknik borç. Ayrıca veritabanı
şu an `create-drop` ve içinde sadece seed verisi var — kırıcı değişiklikleri yapmanın **en ucuz
anı şimdi**. Beta'da gerçek veri varken aynı düzeltmeler migration gerektirir.

### 0.1 — Sırların rotasyonu ve dışarı çıkarılması `[BE/DevOps]` `[AI]`
`application.properties` git'te takip ediliyor ve içinde DB parolası + JWT secret var.
- `application.properties`'i `.gitignore`'a al, `application.properties.example` bırak
- Git **geçmişinden** de temizle (`git filter-repo` veya `BFG`) — dosyayı silmek yetmez
- **Yeni, rastgele 256-bit JWT secret üret** (eskisi yanmış sayılır: onu bilen herkes istediği rol için geçerli token üretebilir)
- `application-dev.properties` / `application-prod.properties` ayrımı + `SPRING_PROFILES_ACTIVE`
- Prod'da `ddl-auto=validate`, `show-sql=false`

### 0.2 — `RegisterRequest` DTO ile mass assignment'ı kapat `[BE]` `[AI + açıklama]`
`UserController.registerUser` ham `User` entity'si alıyor → `role: "ADMIN"` göndererek yetkisiz
admin olunabiliyor; `id` göndererek `save()`'in MERGE davranışıyla mevcut hesabın şifresi ezilebiliyor.
- `dto/request/RegisterRequest` (name, surName, email, password, phone — **`id` ve `role` yok**)
- `UserService.registerUser(RegisterRequest)` → rolü sunucuda `Role.USER` olarak **koşulsuz** set et
- E-posta zaten kayıtlıysa `409 Conflict`
- **Neden DTO:** Entity = veritabanı şekli, DTO = sözleşme şekli. İkisini ayırmazsan istemci
  DB şemasının her alanına yazma hakkı kazanır (Mass Assignment). Bu projedeki DTO deseninin kökü.

### 0.3 — Global exception handling ve hata sözleşmesi `[BE]` `[SEN]`
Şu an `RuntimeException` → istemciye 500 + stack trace; hata gövdesi bazen `String`, bazen HTML.
- `exception/` paketi: `ResourceNotFoundException`, `BusinessRuleException`, `AccessDeniedException` kullanımı
- `@RestControllerAdvice` + `GlobalExceptionHandler`
- Tek tip `ErrorResponse` (timestamp, status, code, message, path) — **stack trace ve iç detay asla dışarı çıkmaz**
- `RuntimeException("...")` çağrılarını anlamlı tiplerle değiştir
- **Neden:** Güvenli hata mesajı bir güvenlik kontrolüdür. "Bu email kayıtlı değil" ile "şifre yanlış"ı
  ayırmak kullanıcı numaralandırma (enumeration) zafiyetidir.

### 0.4 — Method security ve sahiplik kontrolü `[BE]` `[SEN]` ⭐ En kritik adım
`@EnableMethodSecurity` yok, `@PreAuthorize` yok, JWT'deki `role` claim'i hiç okunmuyor.
Tüm korumalı uçlar sadece `.authenticated()`.
- `SecurityConfig`: `@EnableMethodSecurity`, `SessionCreationPolicy.STATELESS`,
  `AuthenticationEntryPoint` ile **401** dön (şu an 403 dönüyor, frontend'in oturum sonlandırması bu yüzden çalışmıyor)
- `AppointmentController`/`AuthController` üzerindeki `@CrossOrigin(origins="*")` anotasyonlarını sil (global config ile çelişiyor)
- `service/AuthorizationService` (veya `OwnershipGuard`): `assertOwnsBusiness(userId, businessId)`
- `service/CurrentUserService`: 3 controller'da tekrarlanan "token'dan kullanıcıyı bul" bloğunu tek yere topla (DRY)
- Şu uçlara sahiplik kontrolü ekle: `/appointments/business/{id}`, `/{id}/pending`, `/{id}/upcoming`, `/businesses/owner/{id}`
- `/appointments/customer/{id}` → path parametresini **kaldır**, `/appointments/me` yap (IDOR'un kökünü kes)
- `GET /api/users` → `@PreAuthorize("hasRole('ADMIN')")` veya tamamen sil
- **Neden path'ten değil token'dan:** Kullanıcının kendi kimliğini URL'de taşıması IDOR'un tanımıdır.
  Kimlik daima sunucunun doğruladığı token'dan gelir.

### 0.5 — ServiceItem yetkilendirmesi ve servis/işletme eşleşmesi `[BE]` `[AI]`
`ServiceItemController`'ın create/update/delete uçlarında sıfır kontrol var — herhangi bir müşteri
rakip işletmenin fiyatını değiştirebiliyor veya hizmetini silebiliyor.
- 0.4'teki `OwnershipGuard` desenini uygula
- `AppointmentService.createAppointment`: seçilen `serviceItem.business.id == businessId` doğrulaması
- `businessId`'nin gerçekten var olduğunu doğrula (`new Business(); setId()` stub'ı yerine gerçek yükleme)
- Silme yerine **soft delete** (`isActive`) — geçmiş randevular hizmete referans veriyor

### 0.6 — Bean Validation `[BE]` `[AI]`
`spring-boot-starter-validation` bağımlılığı bile yok; tek bir `@Valid` yok.
- Bağımlılığı pom'a ekle
- Tüm request DTO'larına `@NotBlank`, `@Email`, `@Size(min=8)`, `@Positive`, `@Future`
- Controller'larda `@Valid`; `MethodArgumentNotValidException`'ı 0.3'teki handler'da alan bazlı 400'e çevir
- **Randevu tarihi geçmişte olamaz** — şu an olabiliyor

### 0.7 — Slot algoritması ve yarış koşulu sertleştirme `[BE]` `[SEN]`
- **Sonsuz döngü/DoS:** `durationInMinutes == 0` ise işaretçi hiç ilerlemiyor → sunucu kilitlenir.
  `/available-slots` `permitAll` olduğu için kimlik doğrulamasız DoS. Hem validation hem
  algoritmada guard.
- `openTime`/`closeTime` null ise NPE → `nullable=false` + guard
- `createAppointment`'a `@Transactional` + `appointments(business_id, appointment_date)` üzerinde
  **unique constraint** (kontrol-sonra-yaz yarış koşuluna DB seviyesinde son savunma)
- **Neden DB constraint:** Uygulama katmanındaki kontrol iki eşzamanlı istekte ikisini de geçirir.
  Doğruluğun tek garantisi veritabanının kendisidir.

**Faz 0 çıktısı:** Kiracılar arası sızıntı yok, yetki yükseltmesi yok, hata mesajları güvenli,
sırlar dışarıda. Beta'ya girilebilir güvenlik tabanı.

---

# FAZ 1 — Mimari Temel

**Hedef:** SOLID ihlallerini kapatmak, veri modelini beta'ya ve Faz 2'ye hazır hale getirmek.
**Neden bu sırada:** Faz 0 kanamayı durdurdu; Faz 2'nin özellikleri (Staff, Review, konum) bu
temelin üstüne kurulacak. Şema kararları gerçek veri gelmeden verilmeli.

### Backend

**1.1 — Response DTO katmanı** `[AI]`
Entity'ler doğrudan response olarak dönüyor. Somut zarar: **herkese açık** `GET /api/businesses`
yanıtında her işletme sahibinin `email`, `phone`, `role` bilgisi çıplak dönüyor (`Business.owner`
EAGER ve `@JsonIgnore`'suz). Frontend bunu kullanmıyor bile.
- `dto/response/`: `BusinessResponse`, `BusinessDetailResponse`, `ServiceItemResponse`,
  `AppointmentResponse` (müşteri PII'si sadece işletme sahibine), `UserResponse`
- `mapper/` paketi — manuel mapper (MapStruct bu ölçekte gereksiz bağımlılık)
- Controller imzaları `List<Business>` → `List<BusinessResponse>`

**1.2 — Katman ihlallerini ve SOLID sorunlarını düzelt** `[SEN]`
- `AppointmentController`'dan `UserRepository`/`AppointmentRepository` bağımlılıklarını kaldır
  (SRP: controller HTTP çevirir, repository'ye service erişir)
- `updateStatus`'taki string tabanlı if/else zincirini enum + durum makinesine çevir
  (OCP: "gelmedi/NO_SHOW" eklerken metodun içi kesilmesin — bu talebi beta'da bekliyorsun)
- `BusinessService.createBusiness`'in gizli rol değiştirme yan etkisini ayrı, açık bir işleme çıkar (SRP)

**1.3 — `AvailabilityCalculator` ayrıştırması** `[SEN]`
`AppointmentService` üç iş yapıyor: rezervasyon, sorgulama, slot hesaplama. Slot algoritmasını
kendi sınıfına çıkar.
- **Neden şimdi:** Faz 2'de bu algoritma personel bazlı çalışacak. Ayrı sınıf olursa hem birim
  testi yazılabilir hem değişiklik tek noktada kalır.
- Slot granülaritesini yapılandırılabilir yap (şu an slotlar hizmet süresine göre kayıyor ve
  rezervasyon sırasına bağlı garip saatler üretiyor — gerçek berber 15/30 dk ızgara ister)

**1.4 — Flyway migration + şema sertleştirme** `[SEN]`
- `ddl-auto=create-drop` → her restart'ta veritabanı siliniyor. Beta'da bir kez unutulursa
  10 işletmenin verisi gider.
- Flyway + `V1__baseline.sql`; prod'da `ddl-auto=validate`
- `price`: `double` → `BigDecimal(10,2)` (para kayan noktayla tutulmaz)
- Index: `appointments(business_id, appointment_date)`, `businesses(category)`
- Seeder'ı `@Profile("dev")` ile sınırla — prod'da asla çalışmasın
- `TestController` ve H2 runtime bağımlılığını kaldır

**1.5 — Business CRUD tamamlama** `[AI]`
- `GET /api/businesses/{id}` (yok — frontend tüm listeyi çekip client'ta filtreliyor)
- `PUT /api/businesses/{id}` (sahiplik kontrollü), `GET /api/businesses/my` (çoklu işletme seçici için)
- `BusinessRequest` DTO ile mass assignment kapatılmış halde

**1.6 — `WorkingHour` entity** `[SEN]`
Şu an tek `openTime`/`closeTime` haftanın 7 günü için geçerli; kapalı gün, öğle molası, farklı
hafta sonu saati modellenemiyor. Gerçek berber dükkanı böyle çalışmıyor.
- `WorkingHour(business, dayOfWeek, openTime, closeTime, isClosed)`
- `BusinessException` tablosu: tatil/özel kapanış günleri
- **Faz 2 hazırlığı:** tablo baştan ayrı olduğu için Faz 2'de nullable `staff_id` eklemek
  migration'sız-acısız olur

### Frontend

**1.7 — Rol farkındalığı ve ortam yapılandırması** `[AI]`
- JWT payload'ını decode edip rolü oku (`jwt-decode`) → `AuthContext`
- Navbar gerçekten reaktif olsun; `BUSINESS_OWNER`/`ADMIN` için **"İstek Kutusu" linki** ekle (şu an menüde hiç yok)
- `RoleProtectedRoute` — `/inbox` sadece işletme sahibine
- Axios interceptor 401 **ve** 403'ü ele alsın
- API adresini `.env` (`VITE_API_URL`) ile dışarı çıkar (şu an `http://localhost:8080` sabit kodlu, deploy'da kırılır)

**1.8 — İşletme paneli iskeleti** `[AI]`
- `PendingAppointments.jsx:28`'deki `const businessId = 1` kaldırılır → `GET /businesses/my` ile işletme seçici
- Hizmet yönetimi ekranı (CRUD)
- Çalışma saatleri ekranı (1.6 ile)

**1.9 — Müşteri "Randevularım" ekranı** `[AI]`
- `/appointments/me` ile geçmiş + yaklaşan randevular, durum rozetleri, iptal butonu

---

# FAZ 2 — Ürün Vizyonunun Tamamlanması

**Hedef:** Vizyondaki eksik parçalar — personel, yorum/puan, konum.
**Neden bu sırada:** Hepsi Faz 1'in DTO, yetki ve şema temeline dayanıyor. Yorum sistemi ayrıca
randevu yaşam döngüsünün tamamlanmış olmasını zorunlu kılıyor.

### 2.1 — Randevu durum makinesi + `COMPLETED`/`NO_SHOW` `[BE]` `[SEN]`
`AppointmentStatus` enum'ında 5 durum var ama `COMPLETED`'a giden **hiçbir kod yolu yok**.
Yorum sisteminin tamamı buna bağlı olacağı için önce bu.
- Geçerli geçişleri tanımlayan durum makinesi (`PENDING→APPROVED`, `APPROVED→COMPLETED|NO_SHOW`,
  `COMPLETED`→ terminal). Şu an iptal edilmiş bir randevu yeniden onaylanabiliyor.
- `NO_SHOW` ekle — "müşteri gelmedi butonu" beta'da beklediğin geri bildirim, ve **no-show yapan
  müşterinin yorum yapamaması** için teknik olarak şart

### 2.2 — Otomatik tamamlama scheduled job `[BE]` `[AI]`
İşletme sahibi her randevuyu elle işaretlemez.
- `@Scheduled` job: `appointmentDate + duration` geçmiş `APPROVED` randevuları `COMPLETED`'a çevirir
- İdempotent olsun (yeniden başlatmada tekrar işlememeli)

### 2.3 — `Staff` entity ve personel CRUD `[BE]` `[SEN]`
- `Staff(business, name, isActive)` + `StaffWorkingHour`
- Personel ekleme/çıkarma, hangi hizmetleri verdiği (`staff_service` bağlantı tablosu)
- Sahiplik kontrolü Faz 0'daki `OwnershipGuard` deseniyle

### 2.4 — Slot algoritmasının personel bazlı hale getirilmesi `[BE]` `[SEN]`
1.3'te ayrıştırılan `AvailabilityCalculator` artık personel başına müsaitlik hesaplar.
- "Fark etmez" seçeneği: en az dolu personele atama
- Bu adım "aynı saate 2 kişi alabilmeliyim" ihtiyacının gerçek çözümü

### 2.5 — `Appointment.staff` ve çakışma kontrolünün personel bazlı olması `[BE]` `[SEN]`
- Nullable `staff_id` + Faz 0.7'deki unique constraint'i `(staff_id, appointment_date)`'e taşı
- Mevcut randevular için migration (nullable → varsayılan personel)

### 2.6 — `Review` entity ve "sadece gitmiş kişi yorum yapar" garantisi `[BE]` `[SEN]` ⭐
**Kural nasıl teknik olarak garanti altına alınır — üç katmanlı savunma:**

1. **Şema katmanı (asıl garanti):** `Review`, `Business`'e değil **`Appointment`'a** `@OneToOne`
   bağlanır ve `appointment_id` üzerinde **UNIQUE constraint** olur.
   → Yorum bir "işletme+kullanıcı" çiftine değil, *belirli bir gerçekleşmiş randevuya* demirlenir.
   Randevusu olmayan yorum yazamaz; bir randevuya iki yorum yazılamaz. Bu, uygulama kodu bozulsa
   bile veritabanının reddettiği bir şeydir.
2. **Servis katmanı:** Yorum oluştururken sırayla doğrula —
   `appointment.customer.id == currentUser.id` (başkasının randevusuna yorum yazamaz),
   `status == COMPLETED` (PENDING/REJECTED/CANCELLED/**NO_SHOW** yazamaz),
   `appointmentDate` geçmişte, ve makul bir zaman penceresi (ör. 30 gün).
3. **Sunum katmanı:** Frontend yorum butonunu sadece `COMPLETED` ve `review == null` olan
   randevularda gösterir. **Bu sadece UX'tir, güvenlik değildir** — asıl kontrol 1 ve 2'dedir.

`Review(appointment, rating 1-5, comment, createdAt)` + yorum metni için uzunluk sınırı ve
XSS'e karşı çıktı kaçışı.

### 2.7 — İşletme puan ortalaması `[BE]` `[AI]`
- Önce repository aggregate sorgusu (`AVG(rating)`, `COUNT`) — basit ve doğru
- Ölçek sorun olursa `Business.ratingAverage`/`ratingCount` denormalize alanları, transaction içinde güncellenir
- **Neden önce basit yol:** Denormalizasyon tutarlılık riski getirir; 5-10 işletmelik beta'da
  aggregate sorgu fazlasıyla hızlı. Erken optimizasyon yapma.

### 2.8 — Konuma göre yakın işletme listeleme `[BE]` `[SEN]`
- `Business.latitude`/`longitude` (`double precision`) + index
- Sorgu: **bounding box ön filtresi + Haversine mesafe** (native query), mesafeye göre sıralı, sayfalı
- **Neden PostGIS değil:** PostGIS güçlü ama ucuz/yönetilen Postgres'lerde eklenti kurulumu ve
  bakım yükü getiriyor. Şehir ölçeğinde (yüzlerce işletme) bounding box + Haversine milisaniyeler
  sürer. İhtiyaç büyürse PostGIS'e geçiş yolu açık kalır.
- Adresten koordinat üretimi: beta'da 5-10 işletme için **elle giriş** yeterli (harita üzerinden pin);
  geocoding API'si sonra

### 2.9 — Kapasitenin gerçekten çalışması: personel bazlı slot hesaplama + panelde personel yönetimi `[BE]` `[AI]`
**Kritik bug:** Faz 2.4/2.5'te personel bazlı çakışma kontrolü ve `calculateForStaff` kuruldu, ama
`AppointmentService.getAvailableTimeSlots` hâlâ eski tek-kaynaklı `calculate()`'ı kullanıyor — 10
personeli olan bir işletme bile pratikte "1 kişilik kapasite" gösteriyor, aynı saatte sadece 1
randevu görünüyor. Personel eklemenin asıl faydası (paralel kapasite) şu an fiilen çalışmıyor.

**Karar (2026-08-23):** Müşteri tarafında personel seçimi/görünürlüğü YOK — atama tamamen görünmez,
"en az dolu personele ata" kuralı sunucu tarafında sessizce çalışır. İleride product ihtiyacı
çıkarsa ayrı bir adım olarak eklenir (bkz. CLAUDE.md karar tablosu).

- Backend: `getAvailableTimeSlots`, işletmenin aktif personeli varsa `calculateForStaff`'a yönlenir;
  personel yoksa (mevcut/eski davranış) `calculate()`'a düşmeye devam eder — geriye dönük tam uyumlu
- Backend: `createAppointment`, personel seçimi göndermeyen bir istekte (bugünkü tek client davranışı)
  müsaitse otomatik en az dolu personele atar
- Frontend: panele "Personel" sekmesi (ServicesTab ile aynı desen) — ekle/çıkar (soft delete),
  hangi hizmetleri verdiğini işaretle. Faz 2.3'ün backend'i zaten hazır, sadece ekran eksik.

### Frontend (Faz 2, devamı)
- **2.10** Yorum/puan bırakma ekranı + işletme kartlarında ve detayında yıldız gösterimi `[AI]`
- **2.11** Tarayıcı konum izni + "yakınımdakiler" sıralaması, izin reddedilirse şehir seçimine düşüş `[AI]`

---

# FAZ 3 — Üretime Hazırlık

**Hedef:** Canlıya güvenle çıkmak ve canlıda ne olduğunu görebilmek.
**Neden en sonda:** Test ve dokümantasyon, üzerine yazılacak API yüzeyi stabilleştikten sonra
yazılırsa boşa emek olmaz. Ama **3.1 ve 3.2 istisna** — mümkünse Faz 1 ile paralel başlat.

### 3.1 — Test altyapısı ve algoritma testleri `[SEN]`
Şu an tek test var ve o da boş `contextLoads()`; üstelik `application.properties` Postgres'e
işaret ettiği için ayakta Postgres olmadan geçmiyor.
- Testcontainers ile gerçek Postgres üstünde entegrasyon testi (H2 ≠ Postgres davranışı)
- **Öncelik: `AvailabilityCalculator` birim testleri** — sıfır süre, kapalı gün, gün sonu taşması,
  sıralı çakışmalar, mola aralıkları. Bu sınıf ürünün kalbi ve regresyon riski en yüksek yer.
- Randevu durum makinesi testleri

### 3.2 — Yetkilendirme entegrasyon testleri `[SEN]` ⭐
Faz 0'da kapattığın her açık için "A işletmesinin sahibi B'nin verisine erişemiyor" testi.
- **Neden ayrı bir kalem:** Bu testler güvenlik regresyon kalkanıdır. Bir yıl sonra yeni bir
  endpoint eklerken sahiplik kontrolünü unutursan seni tek uyaracak şey budur.

### 3.3 — API dokümantasyonu `[AI]`
- `springdoc-openapi` → Swagger UI, JWT auth şeması tanımlı
- Prod'da kapalı veya kimlik doğrulamalı (API yüzeyini herkese ilan etme)

### 3.4 — Bildirim altyapısı — kanal-bağımsız `[BE]` `[SEN]`
Kanal seçimi **ertelendi**. Yapılacak: kanaldan bağımsız iskelet.
- `NotificationPort` arayüzü + `InAppNotificationAdapter` (uygulama içi bildirim tablosu) ve
  `LoggingNotificationAdapter` (dev)
- `NotificationLog` tablosu + idempotency alanı (`reminderSentAt`) — restart'ta çift gönderim olmasın
- `@Scheduled` hatırlatma job'ı: 24 saat sonrası randevuları tarar
- **Neden port/adapter (DIP):** Kanal kararını verdiğinde (SMS/e-posta/WhatsApp) tek bir adapter
  sınıfı yazıp inject edeceksin; iş mantığına dokunmayacaksın. Karar ertelemenin bedeli sıfır olur.
- Kanal seçildiğinde: SMS için İYS/ticari ileti mevzuatına uyum gerekir (izin kaydı, ret hakkı)

### 3.5 — Rate limiting ve kötüye kullanım koruması `[BE]` `[AI]` ✅ tamamlandı
- Login'de brute force koruması: HESAP (5/15dk) ve IP (20/15dk) limitleri **bağımsız** — sadece
  hesap olsa saldırgan farklı hesap dener, sadece IP olsa dağıtık saldırı geçer. Başarılı girişte
  sadece hesap sayacı sıfırlanır, IP sayacı sıfırlanmaz.
- `/register` (10/15dk) ve `/available-slots` (60/dk) IP bazlı, işletme kapak fotoğrafı yükleme
  (5/5dk) kullanıcı id bazlı (uç zaten kimlik doğrulamalı), tüm `/api/**` için 300/dk genel
  güvenlik ağı (IP bazlı, kimlik doğrulanmış istekler dahil, diğer kurallarla üst üste uygulanır).
- Bucket4j yerine bağımlılıksız elle yazılmış sabit-pencere sayaç (`RateLimitPort` arayüzü
  arkasında, bkz. `InMemoryRateLimiter`) — bu projenin çok yeni Spring Boot sürümünde üçüncü
  parti kütüphane sürüm uyumsuzluğu üç kez yaşandı (testcontainers-bom, springdoc-openapi),
  basit bir sayaç algoritmasında bu riski almaya değmedi.
- 429 + `Retry-After` header'ı, mevcut `ErrorResponse` şekliyle tutarlı.
- **Bilinen riskler (kabul edildi):**
  - **Tek instance varsayımı.** Bellek içi limiter tek JVM'e özel — birden fazla instance
    çalışırsa gerçek limit `yapılandırılan × instance sayısı` olur. `RateLimitPort` arayüzü
    sayesinde Redis'e geçiş tek implementasyon değişikliği. ROADMAP'in mevcut deploy planı tek
    sunucu olduğu için şimdilik kabul edilebilir (bkz. Faz 3.8).
  - **Ters vekil arkasında gerçek IP.** Tüm IP bazlı limitler `request.getRemoteAddr()`'ın
    gerçek istemci IP'sini verdiğini varsayıyor — bu da Caddy'nin `X-Forwarded-For` göndermesine
    ve `server.forward-headers-strategy=native`'in doğru çalışmasına bağlı. **Yanlış
    yapılandırılırsa rate limiting koruma olmaktan çıkıp kendi kullanıcılarını kilitleyen bir
    mekanizmaya döner** — tüm kullanıcılar proxy'nin tek IP'si görünür, aynı limiti paylaşıp
    birbirini kilitler. Deploy sonrası ilk kontrollerden biri bu olmalı (bkz. Faz 3.8).

### 3.6 — Loglama, izleme ve hata takibi `[BE]` `[AI]` ✅ tamamlandı
- **Anti dahil, `anyRequest()` boşluğu ayrı bir iş olarak önce ele alındı** (canlı test):
  `SecurityConfig`'in eşleşmeyen path'ler için hiç `anyRequest()` kuralı yok — bu, eski Spring
  Security sürümlerinde bilinen bir tuzak (sessizce korumasız kalma). Geçici bir uçla canlı
  denendi: bu sürüm eşleşmeyen path'i **varsayılan olarak reddediyor** (401) — iddia edilen açık
  gerçek değildi. Kod eklenmedi (`anyRequest().authenticated()` gerçekte hiçbir şeyi
  düzeltmezdi, sadece zaten var olan varsayılanı belgelerdi); bunun yerine davranışın kendisini
  kilitleyen bir regresyon testi yazıldı (`SecurityConfigUnmatchedPathTest`) — ileride Spring
  Security majör sürümü yükselirse veya `SecurityConfig` yeniden yazılırsa test kırılıp haber
  verecek.
- **`logging.structured.format.console=ecs`** — bu Spring Boot sürümünde **native** olarak
  tanınıyor, canlı doğrulandı (JVM'e sistem property olarak geçilip çıktının gerçek ECS-şemalı
  JSON'a döndüğü görüldü). `logback-spring.xml` / `logstash-logback-encoder` gerekmedi. Sadece
  **prod**'da aktif — dev/test'te renkli/okunabilir konsol çıktısı kalıyor. Rolling file
  bilerek yazılmadı: deploy platformu henüz seçilmedi (Faz 3.8), kalıcı olmayan bir disk
  ihtimali işletme kapak fotoğrafındaki aynı riski taşırdı (bkz. CLAUDE.md) — konsola (stdout)
  yazmak bu riski en baştan ortadan kaldırıyor.
- **PII maskeleme — `PiiMasker.maskEmails()`.** Kod tabanında email/phone/JWT'yi DOĞRUDAN
  loglayan hiçbir satır yoktu (grep ile doğrulandı) — bulunan gerçek sızıntı DOLAYLIYDI:
  `GlobalExceptionHandler`'ın `DataIntegrityViolationException` ve `HttpMessageNotReadableException`
  handler'ları, DB sürücüsünün/Jackson'ın ürettiği ham mesajı logluyordu; `users.email` UNIQUE
  kısıtına eşzamanlı iki kayıt isteği çarpınca Postgres'in ürettiği mesaj e-postayı düz metin
  içeriyordu — bu **canlı** eşzamanlı istekle tetiklendi ve doğrulandı. Genel bir "her logu
  regex'le tara" filtresi bilerek yazılmadı; sadece bu iki call site'a uygulandı. Ayrıca **canlı
  testte ikinci, bağımsız bir sızıntı bulundu**: Hibernate'in kendi dahili
  `org.hibernate.orm.jdbc.error` logger'ı, bizim handler'ımızdan tamamen bağımsız olarak aynı ham
  e-postayı ayrı bir satırda basıyordu — `PiiMasker` oraya hiç ulaşamıyordu. Sadece prod'da
  `logging.level.org.hibernate.orm.jdbc.error=OFF` ile kapatıldı (dev/test'te gerçek PII yok,
  hata ayıklama için açık kalıyor); bilgi kaybı yok çünkü constraint adı zaten bizim maskelenmiş
  log satırımızda duruyor — bu da canlı doğrulandı. Detay ve gerekçe: CLAUDE.md karar tablosu
  ("Ham exception mesajı loglama"). Log erişim kontrolü/saklama süresi Faz 3.9'a not düşüldü.
- **Spring Boot Actuator** — sadece `spring-boot-starter-actuator` +
  `management.endpoints.web.exposure.include=health` (başka hiçbir endpoint mapping'e girmiyor)
  + `SecurityConfig`'te `/actuator/health` için tek satır `permitAll`. `/actuator/**` altındaki
  geri kalan her şey zaten yukarıdaki default-deny'e düşüyor — ayrı bir "yasakla" kuralı
  gerekmedi. Canlı doğrulandı: `/actuator/health` → 200 (kimlik doğrulamasız), `/actuator` ve
  `/actuator/env` → 401, mevcut `permitAll` uçları (businesses, swagger) ve korumalı uçlar
  (`/api/appointments/my`) davranış değiştirmedi.
- **Sentry — ertelendi.** Gerekçe: (1) prod deploy henüz yok (Faz 3.8 tamamlanmadı), gerçek
  trafik/kullanıcı olmadan Sentry'nin asıl faydası (canlıda patlayan hatanın anlık bildirimi)
  hiç devreye girmiyor; (2) yeni JSON yapılı loglar zaten tam stack trace + istek bağlamını
  taşıyor, bugünkü tek-instance/tek-geliştiricili aşamada log okumak yeterli; (3) yeni bir
  üçüncü taraf servis/hesap, deploy kararından (Faz 3.8) önce bağlanırsa gereksiz erken bir
  bağımlılık olur. Faz 3.8 deploy tamamlanıp gerçek trafik başlayınca yeniden değerlendirilecek.

### 3.7 — Konteynerleştirme `[DevOps]` `[AI]` ✅ tamamlandı

**Mevcut durum (güncel):** `docker-compose.yml` üç servis çalıştırıyor — `postgres`, `backend`,
`caddy` (frontend'i de sunan reverse proxy). Bu üçü dışında hiçbir servis host'a port açmıyor;
tek genel giriş noktası Caddy'nin 80/443'ü. Aşağıdaki liste, birkaç ayrı gözden geçirme turunda
bulunup düzeltilen gerçek hatalar dahil, şu anki NİHAİ hâli anlatıyor — geçmiş yanlış kararlar
(ör. bind mount) ayrıca not düşülmüyor, sadece doğru sonuç yazılıyor; öğretici olan iki gerçek
yanılgı (Postgres port testi, bind mount izin sorunu) altta ayrı vurgulanıyor çünkü aynı deseni
tekrar etmemek için bilinmesi gerekiyor.

**Backend `Dockerfile`** — multi-stage: `maven:3.9-eclipse-temurin-21` build aşaması (testler
BİLEREK burada çalışmıyor, Testcontainers Docker-in-Docker gerektirirdi), `jarmode=tools` ile
katman çıkarma (`dependencies`/`spring-boot-loader`/`snapshot-dependencies`/`application` —
jar'ı gerçekten build edip içini açarak doğrulandı, varsayımla yazılmadı), final aşama
`eclipse-temurin:21-jre-jammy` (alpine DEĞİL — Thumbnailator/ImageIO'nun musl libc ile bilinen
uyumsuzluk geçmişi var, foto yükleme bu ürünün gerçek özelliği). Root olmayan kullanıcı
(`appuser`, sabit UID/GID 1000) — canlı teyit edildi (`docker compose exec backend id`).
`HEALTHCHECK` ile `/actuator/health`'i kontrol ediyor, Caddy'nin `depends_on: condition:
service_healthy` ile backend'i beklemesi bunun üzerine kurulu.

**`frontend/Dockerfile`** — multi-stage: `node:22-alpine` build aşaması (`VITE_API_URL` build
ARG'i BİLEREK boş, aşağıya bakınız), final aşama `caddy:2-alpine` — Node/npm/kaynak kod final
image'da yok. Prod build'de `.map` dosyası ÜRETİLMİYOR (Vite'ın varsayılanı, proje override
etmiyor — gerçek bir `npm run build` çalıştırılıp doğrulandı, JS bundle'ında `sourceMappingURL`
yorumu da yok).

**`postgres` servisi** — `postgres_data` named volume, `/var/lib/postgresql`'in KENDİSİNE mount
(Postgres 18+ imajı artık `/var/lib/postgresql/data` değil bunu bekliyor — eski konvansiyonla
container "unused mount/volume" hatasıyla açılışta çıktı, canlı yakalanıp düzeltildi).
`healthcheck` (`pg_isready`), host'a port açmıyor.

**`backend` servisi** — host'a port AÇMIYOR (`expose: 8080`, `ports` değil) — backend'e
doğrudan istek atma yolu, bir port unutkanlığına bağlı olmadan en baştan yok. `depends_on
postgres: condition: service_healthy` (düz `depends_on` sadece Postgres konteynerinin
başladığını garanti ederdi, gerçekten bağlantı kabul ettiğini değil). `business_photo_storage`
**named volume** olarak `/app/business-photo-storage`'a mount (aşağıdaki "Öğretici iki yanılgı"
bölümü, madde 2'ye bakınız — bu, ilk seçilip sonra yanlış çıktığı için değiştirilen bir karar).

**`caddy` servisi** — `frontend/Dockerfile` + `Caddyfile`'dan build, 80/443'ü host'a açan tek
servis. `depends_on backend: condition: service_healthy`. Root ÇALIŞIYOR (resmi Caddy image'ının
gerektirdiği gibi — 80/443 gibi ayrıcalıklı portlara bind etmek bunu istiyor) ama
`cap_drop: [ALL]` + `cap_add: [NET_BIND_SERVICE]` + `security_opt: no-new-privileges:true` ile
yetkisi SADECE port bağlamakla sınırlandı (canlı doğrulandı, bu kısıtlamayla hâlâ normal
çalışıyor). Non-root'a TAM geçiş (host portu ayrıcalıksız bir porta çevirip Caddy'yi orada
dinletmek) otomatik HTTPS/ACME akışını bozup bozmadığı gerçek bir domain'le doğrulanmadan
denenmeyecek (Faz 3.8). `caddy_data`/`caddy_config` named volume — TLS sertifika/state kalıcılığı
için (olmadan her container yeniden oluşturmasında Let's Encrypt'ten yeniden sertifika istenir,
haftalık domain başına sınırı zorlayabilir).

**Tüm servisler** — ortak `x-logging` YAML anchor ile Docker'ın json-file log driver'ına sınır
(`max-size: 10m`, `max-file: 3`) — önceden hiç yoktu, sınırsız büyürdü.

**`Caddyfile`** — `/api/*` ve `/actuator` + `/actuator/*` (named matcher — çıplak `/actuator`
`/actuator/*` kalıbına UYMUYOR, bu yüzden ayrı yazıldı; ilk denemede bare path SPA fallback'ine
düşüp `index.html` döndürüyordu, canlı yakalanıp düzeltildi) backend'e reverse proxy. `/api/*`
üzerinde `request_body { max_size 10MB }` — Spring'in kendi 8MB servlet sınırı sadece istek
backend'e ULAŞTIKTAN sonra devreye girdiği için, önünde hiç sınır olmaması çok daha büyük bir
gövdenin (ör. 500MB) backend'e taşınmasına izin veren ucuz bir DoS deseniydi (5MB iş kuralı <
8MB servlet < 10MB Caddy — her katman bir öncekinden gevşek; canlı doğrulandı: 12MB'lik gövde
Caddy'den 413 ile geri döndü, backend'e hiç ulaşmadı). Güvenlik header'ları ELLE eklendi (Caddy
bunları otomatik EKLEMİYOR, sadece otomatik HTTPS/yönlendirme yapıyor): `Strict-Transport-
Security`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`,
`Server` header'ı kaldırıldı (`-Server`) — hepsi `curl -I` ile canlı doğrulandı.
`X-XSS-Protection` bilerek eklenmedi (modern tarayıcılarda kaldırılmış, artık anlamsız).
`Content-Security-Policy` bilerek eklenmedi — bu uygulamaya özel dikkatli ayarlanması gereken
ayrı bir iş, yanlış ayarlanırsa siteyi bozar. Geri kalan her şey statik dosyalar +
`try_files {path} /index.html` (SPA routing — canlı doğrulandı, `/randevularim` gibi bir alt
yola doğrudan gidince 404 değil `index.html` dönüyor).

**Frontend hosting kararı: Caddy ile aynı VPS'te self-host** (Cloudflare Pages değil) —
gerekçe: aynı origin, CORS'u deployed frontend için tamamen gereksiz kılıyor, rate limiting'in
gerçek IP'yi görmesi zaten Caddy'ye bağlıydı, tek platform beta ölçeğinde daha az bakım.
Dev'de de AYNI model: `vite.config.js`'teki `server.proxy`, `/api` ve `/actuator`'ı backend'e
(8080) yönlendiriyor (canlı doğrulandı: 5173 üzerinden gerçek kayıt+giriş çalıştı). Bu yüzden
`VITE_API_URL` TAMAMEN KALDIRILDI — `axios.ts`'te `baseURL` hiç set edilmiyor, `utils/photo.ts`
artık no-op, `frontend/.env`/`.env.example`/`vite-env.d.ts`'teki tip tanımı silindi (tek
içerikleri bu değişkendi).

**CORS — kod değişmedi, sadece değer değişecek.** Aynı origin'de deployed frontend'in kendi
istekleri CORS kontrolüne hiç girmiyor, ama mevcut fail-fast `CORS_ALLOWED_ORIGINS` mekanizması
KALDIRILMADI — JWT `Authorization` header'ı localStorage'da tutulduğu için "başka bir sitenin
JS'i kurbanın token'ıyla API'ye istek atıp yanıtı okuyabilmesi" tehdidine karşı hâlâ koruma
sağlıyor, ileride cross-origin bir istemci (mobil uygulama vb.) gelirse de hazır.

**`.env` yönetimi**: kök dizine `.env.example` (Postgres, JWT_SECRET, CORS_ALLOWED_ORIGINS,
DOMAIN) + kök `.gitignore` (`.env`) eklendi. Gerçek `.env` sunucuya ELLE kopyalanacak (tek
sunucu ölçeğinde bir secrets-manager gereksiz karmaşıklık); docker-compose'un kendi
`${VAR:?...}` fail-fast'i zaten güvenlik ağı.

**`DatabaseSeeder` prod profilinde devre dışı** ✅ zaten yapılmış — `@Profile("dev")` ile sınırlı,
ayrı bir iş gerekmedi.

**Sır sızıntısı canlı doğrulandı** — build edilen her iki image'ın (backend, frontend/Caddy)
içinde `find`/`grep` ile `application-dev.properties`, `.env`, `.git`, gerçek dev şifresi/JWT
secret'ı ARANDI, bulunamadı. Tek bulunan şey zararsız `application-dev.properties.example`
(içeriği açılıp kontrol edildi — sadece placeholder; bkz. CLAUDE.md, "src/main/resources
image'a girer" notu).

**Upload güvenliği canlı doğrulandı.** Same-origin mimarisi + kullanıcı dosyası yüklemesi
birlikte yeni bir stored-XSS sorusu açıyordu (kötü niyetli bir SVG/HTML aynı origin'den servis
edilirse JS çalışır, JWT localStorage'dan çalınabilir). Kontrol edildi, zaten kapalıydı:
`BusinessPhotoService`, istemcinin Content-Type/uzantı iddiasına HİÇ bakmıyor —
`ImageIO.getImageReaders` dosyanın kendi baytlarına (magic bytes) bakıyor, SVG/HTML JDK'nın
okuyucuları tarafından hiç tanınmadığı için doğal olarak reddediliyor; ayrıca her yükleme
`Thumbnails.outputFormat("jpg")` ile SIFIRDAN yeniden çiziliyor ve servis eden uç Content-Type'ı
sabit `MediaType.IMAGE_JPEG` dönüyor — orijinal baytlar hiç saklanmıyor. Canlı doğrulandı: gerçek
bir `<script>` içeren SVG hem kendi uzantısıyla hem `.jpg` kılığında gönderildi, ikisi de 409
ile reddedildi. Tam çözüm (JWT'yi httpOnly cookie'ye taşımak) Faz 3.11'de; bu arada ek bir kod
değişikliği gerekmedi çünkü mevcut doğrulama zaten yeterliydi.

**Uçtan uca canlı doğrulama (gerçek `docker compose up` ile, sadece config okuyarak değil):**
gerçek kayıt+giriş Caddy üzerinden çalıştı; `docker compose exec backend env` ile secret'ların
gerçekten container'a ulaştığı görüldü; Postgres VE fotoğraf depolaması için ayrı ayrı gerçek
veri oluşturulup `docker compose down` (volume korunarak) + `up` sonrası hâlâ orada olduğu
doğrulandı; Caddy'ye sahte bir `X-Forwarded-For` header'ı gönderildi, backend'in gördüğü IP
değişmedi (spoofing'e kapalı) — ama gözlenen IP `172.18.0.1` (Docker bridge gateway'i) çıktı,
bu muhtemelen Windows/Docker Desktop'ın NAT katmanına özgü, gerçek Linux sunucuda farklı
davranabilir (bkz. Faz 3.8'deki rate-limiting IP maddesi); `TempRemoteAddrController.java` (bu
testler için geçici eklenen dosya) hiçbir commit'e girmediği `git log --all --full-history` ve
`git show --stat` ile doğrulandı.

**Öğretici iki yanılgı — aynı desenin iki farklı yerde tekrarı:**
1. **Postgres port testi.** Host'tan `localhost:5432`'ye bağlanmayı denedim, bağlandı — ama
   sebep docker-compose'un port açması değildi (açmıyor, `docker compose ps` ile doğrulandı);
   bu geliştirme makinesinde zaten ayrı, yerel bir Postgres servisi 5432'yi dinliyordu.
2. **`business-photo-storage` bind mount izin hatası.** Aynı hatayı tekrar ettim: bind mount
   seçip "Postgres'inkiyle aynı sınıf koruma" dedim — YANLIŞTI, Postgres named volume kullanıyor,
   ikisi aynı şey değil. Gerçek risk: taze bir Linux sunucuda host'ta `business-photo-storage`
   klasörü yoksa Docker onu `root:root` oluşturur, `appuser` (UID 1000) yazamaz →
   `Permission denied`. **Windows/Docker Desktop'ta bu hiç görünmedi** çünkü orada host dosya
   izinleri container'a gerçek anlamda yansımıyor — ikisi de "temiz görünen bir test, kirli
   ortam yüzünden gerçeği gizliyor" deseninin aynı örneği. Named volume'a geçilerek kapatıldı:
   canlı test edildi, taze bir named volume image'da zaten `chown`'lanmış mount yolundan
   sahipliği otomatik alıyor, appuser hiçbir manuel adım olmadan yazabiliyor; gerçek foto
   yükleyip `down && up` sonrası hâlâ servis edildiği ayrıca doğrulandı. Bedeli: veri artık
   `./backend` altında gözle görülebilir bir klasör değil, Docker'ın yönettiği depolama —
   yedekleme (Faz 3.8, henüz yazılmadı) buna göre bir yardımcı container üzerinden kurulmalı.

### 3.8 — Deploy, yedekleme, izleme `[DevOps]` `[SEN]`

Çalıştırılabilir adım adım prosedür: [RUNBOOK.md](RUNBOOK.md). Bu bölüm sadece kararları ve
kabul kriterlerini takip eder — komut sırası ve "ne görmeliyim" kanıt satırları runbook'ta.

**İki alt faza bölündü, 3.8b'ye 3.8a bitmeden geçilmeyecek:**
- **3.8a** — sunucu kurulumu + sertleştirme + DNS + ilk deploy + doğrulama turu + **uptime
  monitor kurulumu** (bilerek burada — 3.8b'ye ertelenirse 3.8a'nın kendi "48 saat kesintisiz
  uptime" bitiş kriteri hiç sağlanamazdı, kendi kendini bloke eden bir sıra hatası olurdu) +
  **48 saat kesintisiz stabilite** (elle log/fail2ban incelemesi, tek bir uptime düşüşü yok,
  restart döngüsü yok — somut kriter listesi RUNBOOK.md'nin sonunda, "Deploy Sonrası İlk 48
  Saat"; bu pencerede gerçek işletme verisi girilmez, sadece test hesapları).
- **3.8b** — yedekleme + restore provası + kalan izleme (disk uyarısı + yedekleme cron'u için
  dead-man's switch).

Gerekçe: 3.8a olmadan gerçek trafik/veri yok, dolayısıyla yedekleyecek bir şey de yok — sırayı
tersine çevirmenin (önce yedekleme altyapısı kurup sonra deploy etmek) hiçbir faydası yok, sadece
kafa karıştırır. Ayrıca henüz stabil olmadığı kanıtlanmamış bir sistemin yedeğini almanın bir
anlamı yok — 48 saatlik gözlem penceresi bu yüzden 3.8b'nin önünde. 3.8b bitmeden beta
onboarding'e (ve dolayısıyla 3.9/3.10/3.11'e) geçilmeyecek — gerçek müşteri verisi, yedeği
kanıtlanmamış bir sistemde asla işlenmeyecek.

**Windows/Docker Desktop yerel testlerinin kanıt değeri düşük — bu fazda özellikle önemli.**
Faz 3.7'de iki kez aynı desen yaşandı: Postgres 5432 host'tan erişilebilir göründü (aslında bu
makinedeki ayrı bir yerel Postgres servisiydi) ve `business-photo-storage` bind mount izin
sorunu Windows'ta hiç görünmedi (host dosya izinleri container'a gerçek yansımıyor). Bu yüzden
runbook'taki her madde ya **canlı sunucuda doğrulanacak** olarak açıkça işaretli ya da yerelde
doğrulanabilen (ör. `docker compose ps`, timezone testi) gerçek bir kanıtla destekleniyor — hiçbir
adımda "muhtemelen çalışır" cümlesi yok.

#### Beta öncesi kapanması ZORUNLU sorular — 3.8a'ya girmeden kapatıldı

**A. `DatabaseSeeder` prod'da çalışır mı? Hayır — canlı, DB'ye doğrudan bakarak kanıtlandı.**
`@Profile("dev")` class-level anotasyonu var (kontrol edildi) — ama "kod okudum" yeterli
sayılmadı: `SPRING_PROFILES_ACTIVE=prod` ile tamamen taze bir stack açılıp `psql` ile doğrudan
`users`/`businesses` tabloları sorgulandı, ikisi de **0 satır**, loglarda seeder'a ait hiçbir iz
yok. API yanıtına değil DB'nin kendisine bakıldığı için "seeder çalışmadı ama başka bir yoldan
sahte veri girdi" ihtimali de kapandı. Detay: CLAUDE.md karar tablosu.

**B. Flyway migration ortada patlarsa ne olur? Canlı test edildi: temiz ama sonsuz döngü.**
Bilerek bozuk bir migration (V15, var olmayan bir tabloyu ALTER eden) eklenip tamamen boş bir
DB'ye ilk kalkış denendi. Sonuç:
- Postgres DDL'i transactional olduğu için **şema hiç yarım kalmadı** — `"Changes successfully
  rolled back"`, `flyway_schema_history` temiz şekilde bir önceki başarılı sürümde kaldı.
- Ama Spring Boot context'i başlatamayınca JVM çöktü, `restart: unless-stopped/on-failure`
  container'ı yeniden başlattı, o da AYNI bozuk migration'ı tekrar deneyip tekrar çöktü —
  **gerçek bir crash-loop, canlı gözlemlendi** (log'da V15 iki kez art arda aynı hatayla).
- **Kurtarma yolu temiz:** şema hiçbir zaman bozulmadığı için, bozuk migration dosyası
  düzeltilip/kaldırılıp container yeniden başlatıldığında sistem sorunsuz devam ediyor (bu da
  test edildi — dosya silinip rebuild edildi, temiz şekilde V15'e geçti).
- **Asıl risk crash-loop'un SESSİZCE sürmesi** — bu yüzden izleme maddesi (aşağıda, madde 8)
  kritik: `/actuator/health`'i izleyen bir uptime-monitor bu durumu ANINDA yakalar (health hiç
  `200` dönmeyecek çünkü uygulama hiç ayağa kalkmıyor).

**C. V1→V14 zinciri TAMAMEN BOŞ bir veritabanında baştan sona gerçekten çalışıyor mu?
Evet — ayrıca, özel olarak kanıtlandı.** Dev veritabanı zaten migrate edilmiş durumda olduğu
için bu yol günlük kullanımda hiç sınanmıyordu — prod'daki ilk kalkış bu zincirin sıfırdan
uçtan uca ilk gerçek denemesi olacaktı. Postgres volume'u tamamen silinip prod profiliyle
sıfırdan kalkış yapıldı, `flyway_schema_history` doğrudan sorgulandı: **14 migration'ın hepsi
`success=true`**, `\dt` ile 14 uygulama tablosunun hepsi gerçekten oluşmuş görüldü, uygulama bu
şema üzerinde normal yanıt verdi. RUNBOOK.md'nin A8 adımı, sunucudaki ilk gerçek deploy'da bunu
bir kez daha (bu sefer gerçek donanımda) doğrulayacak — yerel test zincirin GEÇERLİ olduğunu
kanıtladı ama sunucudaki asıl çalıştırmanın yerini almıyor, o kontrol A8'de zorunlu bir adım.

**D. Outbound e-posta/SMS gönderen bir kod yolu var mı? Hayır — kontrol edildi, sıfır.**
`pom.xml`'de mail/SMTP/Twilio bağımlılığı yok, hiçbir `.properties` dosyasında mail/smtp config
yok, kod tabanında `JavaMailSender`/`MimeMessage`/`Twilio` hiç kullanılmıyor. `NotificationPort`'un
şu anki tek iki implementasyonu `InAppNotificationAdapter` (DB'ye yazıyor) ve
`LoggingNotificationAdapter` (sadece loglıyor) — ikisi de dışarı ağ çağrısı yapmıyor (Faz 3.4'ün
"kanal kararı ertelendi" kararıyla tutarlı). Yani prod'da eksik bir SMTP kimlik bilgisinden
kaynaklanan bir sessiz-yutma/exception riski **bugün için yok** — bu risk ancak Faz 3.4'te bir
kanal seçilip gerçek bir adapter yazıldığında gündeme gelecek.

#### Mevcut 5 maddede düzeltmeler

1. **Storage adaptörü — bu bir "karar" değil, zaten kapalı bir madde.** `BusinessPhotoStorage`
   arayüzü ve `LocalDiskBusinessPhotoStorage` implementasyonu **zaten mevcut** (kontrol edildi) —
   platform da zaten seçildi (kendi VPS + Caddy, named volume). S3/R2'ye geçiş gerçekten tek bir
   yeni implementasyon + bean değişikliği, ayrıca bir "karar" beklemiyor. 3.8'i bloke eden bir
   madde olarak kaldırıldı.
2. **Yedeklemenin kabul kriteri değişti — "script yazıldı" yeterli değil.** Kabul kriteri: **boş
   bir stack'e (volume'lar sıfırlanmış) restore edilecek, randevular VE fotoğraflar geri
   gelecek.** Bu fiilen yapılıp kanıtlanmadan 3.8b tamamlanmış sayılmayacak. Ayrıca: **rclone
   hedefi aynı VPS'te OLMAYACAK** (Cloudflare R2/Backblaze B2 — sunucu ölürse yedek de ölür,
   bu yedek değil, yanılsama). Detay: 3.8b, RUNBOOK.md.
3. **Rate limiting ön koşulu — zaten var, sadece IP doğruluğu sunucuda kanıtlanmamış.** Caddy'nin
   kendi rate limiting'i yok (doğru tespit — plugin/custom build gerekirdi) ama buna hiç gerek
   yok: backend'de Faz 3.5'te elle yazılmış bir rate limiter (`InMemoryRateLimiter`) zaten var,
   login'de hesap+IP bazlı brute-force koruması **zaten kodda ve çalışıyor** (Bucket4j değil,
   bilinçli tercih — bkz. Faz 3.5 gerekçesi). Eksik olan şey yeni bir faz değil, sadece bu
   korumanın gerçek istemci IP'sini gördüğünün sunucuda doğrulanması (aşağıda, RUNBOOK'ta).

#### Yeni eklenen maddeler

4. **Sunucu sertleştirmesi — listede hiç yoktu, 5 maddenin hepsinden acil.** Container'lar ne
   kadar sıkı olursa olsun host düşerse hepsi düşer. SSH sadece key ile, root login kapalı,
   parola auth kapalı, `ufw` ile sadece 22/80/443 açık, `fail2ban`, `unattended-upgrades`.
   Runbook'un **ilk** bölümü.
5. **Domain + DNS, ACME denemesinden önce.** A kaydı eklenip yayılması beklenmeden Caddy'nin
   gerçek sertifika denemesine geçilmez.
6. **Build stratejisi ve sunucu specs.** CI/registry altyapısı yok (kurmak bu ölçekte orantısız
   karmaşıklık) — build **sunucuda**, `docker compose build` ile yapılacak. Bu makinede ölçülen
   GERÇEK çalışma-zamanı bellek kullanımı (idle, yeni açılmış, eski container'lar rebuild
   sırasında hâlâ ayaktayken): Caddy ~10MB, backend ~347-361MB, Postgres ~42-57MB → toplam
   ~410-430MB — mevcut önerilen **Hetzner CX22 (2 vCPU/4GB/40GB)** için bolca yer var.
   **Build ANININ tepe belleği DENENDİ ama ÖLÇÜLEMEDİ** — bu, "muhtemelen X MB" diye bir tahmin
   değil, gerçek bir ölçüm girişiminin başarısız olduğu anlamına geliyor: `docker stats` VE
   `docker ps -a` ile 40 saniye boyunca saniyede bir kontrol edildi, Maven'in gerçekten
   çalıştığı (log'da "Compiling 131 source files" görüldü) o pencerede **hiçbir build
   container'ı hiçbir zaman görünmedi** — bu Docker Desktop kurulumunda BuildKit'in build
   süreci host'un container listesine hiç yansımıyor. Bu yüzden gerçek tepe değeri **sadece
   sunucuda, `free -m`'i build sırasında saniyede bir örnekleyerek** ölçülebilir (RUNBOOK.md'de
   bu adım var, ölçülen gerçek sayı oraya yazılacak). Önlem: **2GB swap dosyası zorunlu**
   (maliyeti sıfır, kurulumu 2 dakika, ölçülemeyen bir sıçramayı OOM'a çevirmeden yutmak için).
   Sunucuyu küçültmeyin (4GB altı önerilmez).
7. **Rollback.** Image'lar `latest` yerine git SHA ile etiketlenecek (`docker tag`, registry
   gerekmeden, salt SSH komutlarıyla) — bozuk bir deploy'da bir önceki SHA'ya dönülebilsin.
   **Sınır, açıkça yazılacak:** bu SADECE kod/image seviyesinde geri dönüş sağlar; Flyway
   migration'ları geriye alınmıyor (bu projede hiç yapılmadı) — bir deploy şema değişikliği
   içeriyorsa rollback'in kapsamı ona göre daralır. Runbook'un **son** bölümü.
8. **İzleme — faz başlığında vardı, maddelerde yoktu. Hedef netleştirildi: `/`'i DEĞİL,
   `/actuator/health`'i izle.** İlk önerilen "uptime monitor `/`'a pinglesin" tasarımı YANLIŞ
   olurdu — Caddy ayakta olduğu sürece statik `index.html` her zaman 200 döner, backend/Postgres
   tamamen ölü olsa bile. Canlı doğrulandı: Postgres durdurulup `/actuator/health` denendiğinde
   **503** ve `{"status":"DOWN"}` döndü (Spring'in health aggregator'ı `show-details=never`
   olsa bile HTTP durum kodunu DB indicator'ına göre veriyor — body'de detay yok ama status code
   zaten yeterli sinyal); Postgres geri gelince tekrar 200/UP'a döndü. Bu yüzden monitor'ün
   **kesinlikle `/actuator/health`'i** hedeflemesi gerekiyor, madde 11'deki karar bunu zorunlu
   kılıyor (health dışarı kapalıysa bu izleme deseni de kurulamaz). Minimum üçlü: bu health
   ping'i (UptimeRobot, zaten "Önerilen kurulum" tablosunda vardı), disk doluluk uyarısı, ve
   yedekleme cron'u için **dead-man's switch** (healthchecks.io'nun cron-izleme özelliği — cron
   başarıyla bitince ping atar, ping gelmezse alarm verir; sessizce durmuş bir yedekleme, hiç
   olmayandan daha kötü çünkü "var" sanılır). Yukarıdaki B maddesindeki crash-loop senaryosu da
   TAM OLARAK bu health-ping ile yakalanır. **Sıra düzeltmesi (dördüncü geçiş):** health ping'i
   3.8b'de değil **3.8a'da** kuruluyor — 3.8a'nın kendi "48 saat kesintisiz uptime" bitiş
   kriteri, henüz kurulmamış bir aracın verisine bağlı olamazdı, kendi kendini bloke eden bir
   sıra hatası olurdu. Disk uyarısı ve dead-man's switch 3.8b'de kalıyor.
9. **Timezone — canlı test edildi, sorun YOK.** Container'ın OS saati UTC (jammy'nin varsayılanı,
   doğrulandı) ama `TimeConfig`'teki `Clock.system(ZoneId.of("Europe/Istanbul"))` + `TimeZone.
   setDefault(...)` bunu JVM seviyesinde geçersiz kılıyor — container OS'unun TZ'si Java
   tarafında hiç önemli değil. Ayırt edici canlı kanıt: bugün (UTC ~14:09) için "bugün 15:30"a
   randevu denendi — ham UTC saatine göre gelecekte görünürdü (kabul edilmeliydi UTC mantığıyla),
   ama backend "Randevu tarihi geçmişte olamaz" diyerek REDDETTİ çünkü gerçek Istanbul saati
   zaten 17:09'du. Bu, Clock'un container TZ'sinden BAĞIMSIZ doğru çalıştığının kesin kanıtı.
   Frontend tarafı da güvenli: `new Date(dateStr)` + `.getHours()` ikisi de AYNI çalıştırma
   ortamının yerel saatini kullanıyor, bu yüzden hangi cihazda açılırsa açılsın round-trip
   matematik olarak kimlik dönüşümü — **ama bu iddia bu oturumda farklı cihaz saat dilimleri
   için ampirik olarak test EDİLEMEDİ** (Windows'ta Node, `TZ` ortam değişkenini yok sayıyor,
   Docker Desktop izin sorunuyla aynı sınıf bir yerel test kısıtı) — sadece ECMAScript
   spesifikasyonunun garantisine dayanıyor. Container'a ayrıca `TZ=Europe/Istanbul` eklemeye
   GEREK YOK (JVM zaten kendi ayarını okumuyor bile).
10. **`restart: unless-stopped` — kontrol edildi, EKSİK.** Şu an sadece backend ve caddy'de
    `restart: on-failure` var (postgres'te hiç yok), ve `on-failure` bir VPS reboot'undan sonra
    container'ları OTOMATİK başlatmaz (sadece çöken bir container'ı yeniden dener, durdurulmuş
    bir daemon'dan sonra değil). Üç servise de `restart: unless-stopped` eklenmesi gerekiyor —
    reboot testi runbook'un doğrulama turunda var.
11. **Prod secret'ları sunucuda YENİDEN üretilecek.** Dev'deki JWT secret ve DB şifresi asla
    sunucuya taşınmıyor — `openssl rand -base64 32` (JWT) ve benzeri komutlarla sunucuda taze
    üretilip `.env`'e yazılıyor (`chmod 600`). Komutlar RUNBOOK.md'de.

#### Karar: `/actuator/health` dışarıya açık kalsın mı? — ✅ ONAYLANDI, açık kalacak

- **Açık bırakmanın artısı:** dış bir uptime-monitor (UptimeRobot/healthchecks.io) periyodik
  ping atıp düşüşü mail ile bildirebilir — Docker'ın kendi `HEALTHCHECK`'i sadece container
  içinden çalışıyor, dışarıdan "site tamamen erişilemez" durumunu (ör. Caddy'nin kendisi
  çökerse) YAKALAYAMAZ.
- **Artısı olmayan taraf:** risk zaten düşük (`show-details` hiç ayarlanmamış, body detayı hiç
  sızmıyor). Ama bu ayrıntı önemli: `show-details=never` sadece GÖVDEYİ (`components` alanını)
  gizliyor — **HTTP durum kodu yine de DB durumuna göre değişiyor** (canlı doğrulandı: Postgres
  durunca `/actuator/health` **503** + `{"status":"DOWN"}` döndü). Yani dışarı açık health,
  izlemenin gerçek anlamda işe yaraması için ZORUNLU — kapalı olsaydı dış monitor'ün DB'nin
  çöktüğünü fark etmesinin bir yolu kalmazdı.
- **Karar: açık kalıyor.** Madde 8'deki izleme, doğrudan bu health endpoint'ini hedefleyecek.

#### Not (bloklayıcı değil) — `InMemoryRateLimiter`'ın ölçek sınırı

`InMemoryRateLimiter` (Faz 3.5) her deploy'da (container yeniden başladığında) sayaçları
sıfırlıyor ve **birden fazla backend instance'ı olursa tamamen bozuluyor** (her instance kendi
belleğinde ayrı sayıyor — saldırgan farklı instance'lara denk gelerek limiti aşabilir). Beta
ölçeğinde (tek instance, tek sunucu) sorun değil — ama sistem ölçeklenip ikinci bir backend
instance'ı eklenirse bu **sessizce** işe yaramaz hale gelir, sürpriz olmasın diye buraya not
düşülüyor. O noktada `RateLimitPort` arayüzü sayesinde Redis'e geçiş tek implementasyon
değişikliği (aynı `BusinessPhotoStorage`/S3 deseni).

### 3.9 — KVKK ve hukuki metinler `[SEN]`
Gerçek kişilerin ad, telefon ve randevu geçmişini işleyeceksin.
- Aydınlatma metni, açık rıza akışı, gizlilik politikası, kullanım şartları
- VERBİS kayıt yükümlülüğü eşiğini kontrol et
- İşletmelerle veri işleyen sözleşmesi (sen veri sorumlususun, işletme de öyle)
- **Hesap ve veri silme akışı (unutulma hakkı) — `USER` VE `BUSINESS_OWNER` için, backend
  implementasyonu VE testleri tamamlandı (bkz. altta "Uygulama durumu").** Frontend (silme
  UI'ı, askıya alınmış işletme linkinin düz metne dönmesi) henüz yapılmadı.

  **Tespit edilen teknik kısıt:** Hiçbir migration'da `ON DELETE CASCADE`/`SET NULL` yok — hepsi
  düz `REFERENCES` (Postgres varsayılanı `RESTRICT`). Yani randevusu/favorisi olan bir
  `users` satırını hard-delete etmek şu an zaten mümkün değil, DB FK ihlaliyle reddediyor.
  Bu yüzden **hard delete değil, anonimleştirme** — KVKK/GDPR'ın da standart çözümü, hem FK
  sorununu çözüyor hem işletmenin randevu geçmişi gibi meşru çıkarını koruyor. `ReviewMapper`
  yorumcu adını HER SEFERİNDE canlı `appointment.getCustomer().getName()`'den okuyor (dondurulmuş
  kopya değil, kontrol edildi) — User'ın alanları anonimleşince herkese açık yorumlardaki isim
  de otomatik değişir, ayrı bir kod değişikliği gerekmiyor.

  **Akış — gecikmeli anonimleştirme, 30 gün boyunca HİÇBİR ŞEY DEĞİŞMEZ (bekleme süresi
  configurable, `application.properties`, varsayılan 30 gün — koda gömülmez,
  `AppointmentPolicyProperties` deseniyle aynı). Tasarım bir kez düzeltildi — aşağıdaki
  "Düzeltme" notuna bakınız, önceki taslak hesabı anında kilitliyordu, bu geri dönüşü
  imkansız kılardı.**
  1. `DELETE /api/users/me` — şifre tekrar istenir (geri alınamaz bir işlem, ele geçirilmiş bir
     oturumla tetiklenmesin). `Role.USER` için akış budur; `Role.BUSINESS_OWNER` için AYNI uç,
     aşağıdaki ek adımlarla birlikte çalışır (bkz. "BUSINESS_OWNER silme akışı"). `Role.ADMIN`
     isteği reddedilir — operatör hesapları self-servis silinmiyor.
  2. `User.deletionRequestedAt = now()` yazılır (yeni alan). **Bunun DIŞINDA hiçbir şey
     değişmez** — giriş kapanmıyor, randevular iptal edilmiyor, hesap normal çalışmaya devam
     ediyor. Giriş yapınca ekranda "Hesabınız [tarih]'te silinecek — İptal Et" bandı görünür.
  3. **`POST /api/users/me/cancel-deletion`** (yeni) — normal, kimlik doğrulamalı bir istek,
     `deletionRequestedAt`'i `null`'a çeker. Bu, hesap ele geçirilip silme tetiklenirse gerçek
     sahibinin kurtarma yolu: hesap 30 gün boyunca TAM OLARAK bırakıldığı gibi durduğu için
     (hiçbir randevu iptal edilmemiş, hiçbir alan değişmemiş), giriş yapıp iptal ettiğinde
     hesabını nasıl bıraktıysa öyle bulur. Bu akış bir e-posta linkine değil, normal şifreyle
     girişe dayanıyor — outbound e-posta/SMS zaten yok (Faz 3.8'de doğrulandı), ama zaten
     gerekmiyor da: iptal, hesabın KENDİSİNE giriş yaparak yapılıyor.
  4. **`AccountDeletionScheduler`** (yeni, `AppointmentLifecycleScheduler` ile aynı desende) —
     periyodik olarak `deletionRequestedAt IS NOT NULL AND anonymizedAt IS NULL AND
     deletionRequestedAt <= now() - gracePeriod` olan kullanıcıları bulup, TÜM aşağıdaki
     adımları AYNI ANDA (30. günde, tek seferde) uyguluyor — 2. adımdan bu yana ilk kez bir
     şey değişiyor:
     - Kullanıcının bitmemiş randevuları (`PENDING` + gelecekteki `APPROVED`) mevcut
       `AppointmentService.changeStatus(..., Action.CANCEL)` yolu ile `CANCELLED`'a geçirilir.
     - `name`/`surName` → "Silinmiş Kullanıcı", `email` → `deleted-user-{id}@deleted.local`
       (ID kullanmak benzersizliği garanti ediyor, ayrı bir token üretmeye gerek yok),
       `phone` → placeholder, `password` → rastgele/kullanılamaz bir hash.
     - Spring Security'nin `UserDetails`'ine `enabled=false` bağlanır (`anonymizedAt != null`
       iken) — artık gerçekten giriş kapanıyor, bu noktadan sonra geri dönüş yok zaten.
     - `User.anonymizedAt = now()` yazılır (işlemin tamamlandığının kaydı).
     - Favoriler hard-delete edilir (başka hiçbir satır bağımlı değil, saklama değeri yok).
     - Randevu/yorum/bildirim kayıtlarına DOKUNULMAZ — anonimleşmiş `User` satırına FK ile
       bağlı kalmaya devam ederler, işletmenin operasyonel geçmişi bozulmaz.
  5. **Fatura/muhasebe kaydı bu akışın DIŞINDA** — bu uygulama şu an ödeme/fatura işlemiyor,
     ileride eklenirse o veri ayrı bir saklama kuralına tabi olacak, anonimleştirme ona hiç
     dokunmayacak.

  **Düzeltme (önceki taslaktan) — geri dönüş neden 30 gün boyunca TAM olmalı.** İlk taslakta
  hesap istek ANINDA `enabled=false` ile kilitleniyordu ("bekleme süresi kullanıcı için değil
  sistem için" diye gerekçelendirilmişti) — bu YANLIŞTI. Asıl senaryo şu: hesabı ele geçiren
  biri silme isteğini tetiklerse, gerçek sahibi bu pencerede giriş yapıp iptal edebilmeli VE
  hesabını AYNEN bıraktığı gibi bulmalı. İlk gün herhangi bir şey (giriş kilidi, randevu
  iptali) uygulanırsa bu geri dönüş imkansızlaşır. Düzeltilmiş tasarımda 30 gün boyunca
  GERÇEKTEN hiçbir şey değişmiyor — sadece tek bir zaman damgası yazılıyor, geri kalan her
  şey (giriş kapama, randevu iptali, alan scrub'ı) 30. günde TEK seferde, atomik olarak
  uygulanıyor.

  **Ek soru: silme talebi verildiği AN (henüz anonimleştirme yok, `enabled` hâlâ true) mevcut
  JWT'ler geçerli kalmaya devam ediyor — bu bilinçli bir kabul, düzeltilecek bir açık değil.**
  Karar: `requestDeletion` var olan token'ları GEÇERSİZ KILMIYOR (ve kılmamalı). Gerekçe: (1)
  `DELETE /api/users/me` zaten şifre yeniden istiyor — şifresiz biri bu talebi tetikleyemez,
  yani "ele geçirilmiş ama şifresiz bir oturum" senaryosu burada geçerli değil. (2) Gerçek
  hesap sahibi, eski token'ı geçerli kalsın ya da kalmasın, `enabled=true` olduğu sürece
  şifresiyle YENİDEN login olup `POST /api/users/me/cancel-deletion`'a ulaşabilir (login,
  `DaoAuthenticationProvider` üzerinden HER SEFERİNDE taze bir `isEnabled()` kontrolü yapar) —
  yani "eski token'lar canlı kalsın" kararı kurtarma/geri dönüş akışını hiçbir şekilde
  ENGELLEMİYOR. (3) Bu projede JWT tamamen stateless — hiçbir yerde (şifre değişikliğinde bile,
  bkz. `UserService.changePassword`) "bu andan önce üretilmiş token'lar geçersiz" türünde bir
  mekanizma (ör. `User` üzerinde bir `tokenValidAfter` alanı + `JwtFilter`'da bunun kontrolü)
  yok. Böyle bir mekanizmayı SADECE silme talebi için eklemek tutarsız olurdu — şifre
  değişikliğinde neden yok da burada var sorusuna cevap üretilemezdi. Genel bir oturum
  sonlandırma/token-geçersiz-kılma ihtiyacı gerçekten doğarsa (ör. "şüpheli giriş" özelliği),
  bu HER İKİ olayı (şifre değişikliği VE silme talebi) kapsayan ayrı, daha geniş bir görev
  olmalı — 3.9'a özel bir yama değil. (Bu, anonimleştirme SONRASI eski token'ların ne olduğu
  sorusundan AYRI — o soru zaten canlı test edilip düzeltildi, bkz. `JwtFilter`'daki
  `UsernameNotFoundException` yakalama ve `JwtFilterAnonymizedUserTest`.)

  **Anonimleştirme gerçekten geri döndürülemez mi? Kod tabanında kontrol edildi: canlı
  veritabanında evet, yedeklerde HAYIR (dürüstçe kabul edilen tek sınır).**
  - `NotificationLog`/`InAppNotification` tabloları kontrol edildi: ikisi de kullanıcıyı
    sadece `recipientUserId` (FK) ile tutuyor, isim/e-posta/telefon KOPYASI yok. Bildirim
    metinleri şablon (`"{işletme adı} işletmesinde randevunuz yaklaşıyor"` gibi) — müşterinin
    KENDİ adını hiç içermiyor. Normal (hata dışı) hiçbir `log.info/warn/debug` satırı
    `getEmail()`/`getName()`/`getPhone()` basmıyor (grep ile tüm kod tabanı tarandı, sıfır
    sonuç). Yani canlı DB dışında, uygulamanın kendi tablolarında veya normal loglarında
    gizli bir PII kopyası YOK — anonimleştirme çalıştığında canlı sistemde gerçekten iz
    kalmıyor.
  - **Ama yedekler ayrı bir gerçek.** Faz 3.8'in yedekleme planı ("7 günlük + 4 haftalık
    saklama") anonimleştirmeden ÖNCE alınmış bir `pg_dump`'ı hâlâ tutuyor olabilir — o
    yedeğin içinde kullanıcının gerçek adı/e-postası/telefonu, o yedek kendi rotasyon
    süresiyle (en fazla ~4 hafta) silinene kadar durur. Bu, bu projeye özgü bir eksiklik
    değil — "silinen veri yedeklerde bir süre daha durur" KVKK/GDPR uygulamalarında genel
    kabul gören bir sınır, yedekleri geriye dönük düzenlemek (compressed arşiv dosyalarını
    tek tek işlemek) pratik değil. Dürüst özet: **canlı sistemde anonimleştirme anında ve
    geri döndürülemez; TÜM kopyalar (yedekler dahil) üzerinden tam silinme, o yedeklerin
    kendi rotasyon takvimine göre ek ~4 hafta sürebilir.**
  - **Restore sonrası tekrar anonimleştirme — 3.9 kapsamına giren ayrı bir madde.** Bir yedek
    (ör. felaket kurtarma, veri bozulması) geri yüklendiğinde, o yedek anonimleştirmeden ÖNCEKİ
    hâli taşıyorsa, restore işlemi silinen PII'yi **sessizce** geri getirir — bu, yedekleme
    planının kendisinden ayrı, GERÇEK bir prosedür eksikliği. 3.9'a şu madde eklenmeli: her
    restore sonrasında, `anonymizedAt IS NOT NULL` olan kullanıcıları bulup alanlarını
    (`name`/`surName`/`email`/`phone`/`password`) YENİDEN scrub eden bir adım/script
    çalıştırılacak — restore prosedürünün (RUNBOOK.md'nin B4'ü) son adımı olarak. Not: bu,
    `anonymizedAt` zaman damgasının (adım 4'te zaten yazılıyor) tam olarak bu yüzden ayrı bir
    sütun olarak tutulduğunu doğruluyor — "kim anonimleşmiş" sorusunu restore sonrası tekrar
    sormak için gereken tek bilgi bu.

  **`BUSINESS_OWNER` silme akışı — backend implementasyonu VE testleri tamamlandı.**

  **Veri modeli tespiti.** `Business.owner` `@ManyToOne` — **bir sahip N işletme
  yönetebiliyor** (`BusinessRepository.findByOwnerId` → `List<Business>`, CLAUDE.md karar
  tablosuyla tutarlı). `Staff` ve `ServiceItem` doğrudan `Business`'a bağlı (`business_id`),
  `User`'a değil — `Staff`'ın kendi `name` alanı var, sisteme hiç kayıtlı bir kullanıcı değil
  (bir berber dükkânındaki çalışan, platformun kendi hesabı olmak zorunda değil). **Bu yüzden
  personel kayıtlarına HİÇ dokunulmuyor** — sahibin kendi hesap silme isteğiyle ilgisizler.
  `Business.serviceItems`'ta Hibernate seviyesinde `cascade=ALL` var ama DB'deki gerçek FK
  (`appointments.service_id REFERENCES service_items`) yine düz `RESTRICT` — yani randevu
  geçmişi olan bir işletme de, `User` gibi, hard-delete edilemez. Aynı kısıt bir seviye
  yukarıda da geçerli.

  **Randevu zamanlaması — iki ayrı eşik, KARIŞTIRILMAMASI gereken iki farklı kavram. İkisi de
  `AccountDeletionProperties`'e config olarak eklenir (koda gömülmez):**
  - **Haber verme payı** — `app.account-deletion.business-notice-period` (varsayılan **72
    saat**). Talep ANINDA, önümüzdeki 72 saat içindeki randevular iptal edilir + müşteriye
    bildirim. Hiçbir müşteri randevusuna saatler kala öğrenmemeli.
  - **Geri dönüş penceresi** — `app.account-deletion.business-reversal-window` (varsayılan
    **48 saat**). Talep 48 saat içinde geri alınmazsa, KALAN tüm gelecek randevular iptal
    edilir + bildirim.

  **Matematiksel zorunluluk — `reversal-window` HER ZAMAN `notice-period`'dan küçük veya eşit
  olmalı (48 ≤ 72).** Bu tesadüf değil, tasarımın kendisi: 48. saatte topluca iptal edilen
  "kalan tüm randevular" tanım gereği talep anında 72 saatten UZAKTA olan randevulardı (72
  saatten yakın olanlar zaten 0. saatte ayrı ayrı iptal edilmişti) — yani 48. saatte hâlâ en
  az `72−48=24` saat, çoğunlukla çok daha fazla payları var. Bu ilişki TERSİNE dönerse (ör.
  `reversal-window=96s`, `notice-period=72s` olsaydı) 72-96 saat arasına düşen bir randevu
  HİÇBİR aşamada zamanında yakalanamaz, müşteri randevu gününe kadar sistemin hiç
  cevaplamadığı bir talep bekler. Bu yüzden `AccountDeletionProperties`'in `@PostConstruct`
  doğrulaması `reversalWindow > noticePeriod` durumunu reddedip güvenli varsayılana düşecek
  (aynı `AvailabilityCalculator.effectiveGranularity` deseni) — bu, koda gömülü bir sabit
  değil, YANLIŞ configure edilebilecek iki ayrı değer arasındaki bir İLİŞKİ, o yüzden ayrıca
  doğrulanması gerekiyor.

  **Kimlik anonimleştirmesi (30 günlük `gracePeriod`) bu ikisinden TAMAMEN AYRI ve
  DEĞİŞMEDİ — teyit edildi.** Randevu kaderleri 48-72 saat içinde netleşiyor, ama `User`
  satırının kendisi (isim/e-posta/telefon) hâlâ USER akışındaki AYNI 30 günlük pencereyi
  bekliyor, hesap hâlâ giriş yapılabilir durumda kalıyor. İkisi farklı şeyi koruyor: randevu
  iptali müşteriyi korur (hızlı olmalı), anonimleştirme hesap sahibinin geri dönüş hakkını
  korur (geniş olmalı).

  **Bildirim — yeni bir `NotificationType`.** Her iki dalgada da (72s anında iptal, 48s toplu
  iptal) etkilenen müşteriye in-app bildirim gidiyor — mevcut `NotificationType` enum'ında
  buna uyan bir değer yok, yeni bir tür eklenmesi gerekiyor (ör.
  `APPOINTMENT_CANCELLED_BUSINESS_CLOSED`), `APPOINTMENT_EXPIRED` ile aynı desende
  (`InAppNotificationAdapter` üzerinden, `NotificationLog` ile idempotent).

  **Akış:**
  1. `DELETE /api/users/me` (USER akışıyla AYNI uç, rol bazlı dallanma) — şifre tekrar istenir,
     `User.deletionRequestedAt = now()` yazılır.
  2. **Aynı istek içinde, senkron olarak (bekletilmeden):**
     - Sahibin TÜM işletmeleri (`findByOwnerId`) yeni bir `Business.suspendedAt = now()`
       alanıyla işaretlenir.
     - Bu işletmelere ait, tarihi `now() + 72 saat` içine düşen TÜM randevular (`PENDING` +
       `APPROVED`) mevcut `AppointmentService.changeStatus(..., Action.CANCEL)` ile
       `CANCELLED`'a geçirilir + müşteriye bildirim.
  3. **`business-reversal-window` (48 saat) dolunca** — `AccountDeletionScheduler`'a eklenen
     yeni bir tik: `deletionRequestedAt` hâlâ dolu (yani iptal edilmemiş) olan
     `BUSINESS_OWNER`'ların işletmelerindeki KALAN TÜM bitmemiş randevular (`PENDING` +
     `APPROVED`, tarihi ne olursa olsun) aynı `CANCEL` yoluyla topluca iptal edilir + bildirim.
     Doğal olarak idempotent — ikinci bir tick'te sorgu zaten sadece `PENDING`/`APPROVED`
     aradığı için (bunlar bir önceki tick'te `CANCELLED`'a döndüğü için) tekrar bir şey
     bulmaz, ayrı bir "yapıldı mı" bayrağı gerekmiyor.
  4. **`POST /api/users/me/cancel-deletion`** (USER akışıyla AYNI uç) — `deletionRequestedAt`'i
     temizler VE sahibin işletmelerindeki `suspendedAt`'i de temizler (yeniden listelenirler).
     **Dürüstçe kabul edilen sınır:** 2. veya 3. adımda ZATEN iptal edilmiş randevular GERİ
     GELMEZ — o karar geri alınamaz (slot başka birine gitmiş olabilir), sadece hesabın ve
     işletmenin kendisi normale döner. Bu, USER akışında "iptal edilen randevu geri gelmez"
     ile aynı, önceden kabul edilmiş maliyet.
  5. **30. günde (`gracePeriod`, USER akışıyla PAYLAŞILAN aynı süre, yukarıdaki notu bakınız)**
     — `AccountDeletionScheduler`'ın mevcut anonimleştirme adımı: `User` satırı (isim/
     e-posta/telefon/şifre) USER akışındaki AYNI şekilde scrub edilir, giriş kapanır.

  **Business'ın KENDİ alanları — KARAR VERİLDİ: dokunulmuyor.** Ad/telefon/adres ticari veri,
  zaten kamuya açıktı — anonimleştirme sadece `User` satırını kapsıyor.

  **Ama silinmiş bir işletmenin profili HİÇBİR YOLLA görüntülenemeyecek — "aramada
  görünmüyor" yeterli değil, üç ayrı yol da kapatılmalı:**
  - **Arama/listeleme:** `GET /api/businesses` ve konum/kategori sorguları `suspendedAt IS
    NULL` filtreler (zaten plandaydı).
  - **Doğrudan URL:** `GET /api/businesses/{id}` (detay ucu, `BusinessService.getBusinessById`)
    `suspendedAt != null` ise **404** döner (403 değil — path traversal'daki gibi, "var ama
    erişemiyorsun" ile "böyle bir şey yok" arasında fark belli edilmez). **Yapıldı.**
  - **Eski randevu detayında tıklanabilir link — henüz yapılmadı (frontend).** Backend'in
    404'ü zaten gerçek güvenlik sınırı (frontend ne yaparsa yapsın tıklanınca 404 alınır) —
    ama frontend'de de `BusinessSummary`'nin (`AppointmentResponse.business`) taşıdığı bilgiye
    `suspendedAt`/`active` gibi bir alan eklenip, geçmiş randevu ekranlarında işletme adı
    `suspendedAt` doluysa tıklanabilir link DEĞİL düz metin olarak gösterilmeli — kırık bir
    link gibi görünmesin.

  **Uygulama durumu (backend, teyit edildi — test edilerek doğrulandı, iddia değil):**
  - Migration `V15__account_deletion.sql` — `users.deletion_requested_at`, `users.anonymized_at`,
    `businesses.suspended_at` (üçü de nullable, expand-contract).
  - `AccountDeletionProperties` (`app.account-deletion.grace-period`/`business-notice-period`/
    `business-reversal-window`, `application.properties`'te varsayılanlarıyla) —
    `@PostConstruct` doğrulaması hem tekil değerleri hem `reversalWindow <= noticePeriod`
    ilişkisini kontrol ediyor.
  - `AccountDeletionService` (`requestDeletion`/`cancelDeletion`/`bulkCancelRemainingAppointments`/
    `anonymize`) ve `AccountDeletionScheduler` (`bulkCancelAfterReversalWindow`/
    `anonymizeAfterGracePeriod`, `AppointmentLifecycleScheduler` ile aynı cron'u paylaşıyor).
  - `DELETE /api/users/me` ve `POST /api/users/me/cancel-deletion` (`UserController`).
  - `CustomUserDetailsService`: `enabled = (anonymizedAt == null)` — Spring Security'nin kendi
    `DisabledException` mekanizması, elle kontrol yok.
  - `BusinessRepository`/`BusinessService`/`LocationService`: liste, kategori ve konum
    sorguları `suspendedAt IS NULL` filtreli; `findByOwnerId` BİLEREK filtresiz (sahip kendi
    `/my` panelinden görmeye devam etmeli).
  - `AppointmentService.createAppointment`: askıdaki işletmeye yeni randevu denemesi
    `BusinessRuleException` (409) ile reddediliyor.
  - **Askıdaki işletme sahibi panelde ne yapabilir — karar verildi: salt okunur + sadece
    "talebi iptal et" aktif.** `OwnershipGuard`'a mutasyon uçları için ayrı bir metot ailesi
    eklendi (`assertOwnsActiveBusiness`/`assertOwnsActiveServiceItem`/`assertOwnsActiveStaff`)
    — sahiplik YETMEZ, işletme ayrıca askıda olmamalı. Okuma uçları (`assertOwnsBusiness`/
    `assertOwnsServiceItem`/`assertOwnsStaff`) DEĞİŞMEDİ, askıda olsa da geçer — sahip kendi
    randevularını/personel listesini/inbox'ını görmeye devam eder. Mutasyon uçları
    (`BusinessController.updateBusiness`/`uploadPhoto`/`removePhoto`,
    `WorkingHourController.setWorkingHour`/`addClosure`/`removeClosure`,
    `ServiceItemController.create/update/delete`, `StaffController.create/update/delete` +
    personel çalışma saati) hepsi aktif-varyanta geçirildi. `AppointmentService.changeStatus`:
    askıdaki işletme sahibi artık APPROVE/REJECT/NO_SHOW yapamaz (409) — CANCEL BİLEREK
    istisna, hem müşteri kendi randevusunu her zaman iptal edebilmeli hem de
    `AccountDeletionService`'in kendi otomatik iptalleri aynı yolu (sahibinin ID'siyle)
    kullanıyor. `BusinessService.createBusiness`: silme talebi olan bir kullanıcı yeni işletme
    açamaz.
  - **`businessRepository` çağrı noktaları tarandı — `findByOwnerId` DIŞINDA filtresiz kalan
    yok.** Diğer tüm `findById` çağrıları (createAppointment, updateBusiness, swapPhotoKey,
    FavoriteService, ServiceItemService, StaffService, OwnershipGuard, WorkingHourService) ya
    tekil id ile zaten kimliği bilinen bir işletmeye erişiyor (IDOR değil, sahiplik ayrı
    kontrol ediliyor) ya da sahibin kendi panel işlemi.
  - **Bulgu kapatıldı: `GET /api/service-items/business/{id}`, `GET /api/businesses/{id}/working-hours`
    ve `.../closures` artık kimliksiz/sahipliksiz DEĞİL.** İlk taramada bu üç uç "bilerek
    herkese açık" sanılmıştı ("müşteri randevu almadan önce saatleri görebilmeli" gerekçesiyle,
    bkz. eski `SecurityConfig` yorumu) — ama frontend'de gerçekten kontrol edildi
    (`BusinessDetailPage.tsx` grep'lendi): müşteri sayfası bu uçları HİÇ çağırmıyor, sadece
    `GET /api/businesses`'in gömülü `serviceItems`/`openTime`/`closeTime` alanlarını kullanıyor.
    Tek gerçek çağıran işletme sahibinin kendi paneli (`WorkingHoursTab.tsx`/`ServicesTab.tsx`) —
    yani "aynı uç hem herkese hem sahibe açık" değil, "hiç kimseye açık olması gerekmeyen bir uç
    yanlışlıkla herkese açıktı" durumu. Tıpkı daha önce aynı sebeple kilitlenen
    `StaffController.getStaffByBusiness` gibi: `SecurityConfig`'ten iki `permitAll` satırı
    kaldırıldı, üçü de artık `OwnershipGuard.assertOwnsBusiness` (okuma varyantı, Active DEĞİL —
    sahip kendi askıdaki işletmesini görmeye devam eder) ile korunuyor. Görünüm ayrımına HİÇ
    gerek kalmadı çünkü tek gerçek görünüm zaten sahibinki. Canlı MockMvc testiyle doğrulandı
    (`OwnerOnlyEndpointsUnauthenticatedTest`): kimliksiz istek üçünde de 401.
  - **Testler — kullanıcının istediği 5 senaryo + 2 ek soru (idempotency, anonimleştirilmiş
    hesapta cancelDeletion) + askıdaki işletme mutasyon kısıtı + üç ucun kilidi,
    `Clock` manipülasyonuyla (`AccountDeletionIntegrationTest` gerçek Testcontainers Postgres,
    `OwnershipGuardTest`/`AppointmentServiceStateMachineTest`/`OwnerOnlyEndpointsUnauthenticatedTest`
    birim/MockMvc), hepsi yeşil (142/142):**
    47. saatte geri alma → hiçbir randevu iptal olmadı + hesap normale döndü, bildirim
    gitmedi; 49. saatte scheduler → geri dönüş penceresi dolduğu için kalan randevular iptal +
    müşteriye `APPOINTMENT_CANCELLED_BUSINESS_CLOSED` bildirimi `SENT` loglandı; talep anında
    72 saat içindekiler hemen iptal + bildirim, 73. saatteki dokunulmadan kaldı + bildirim
    gitmedi; askıdaki işletmeye randevu denemesi reddedildi; 30. günde anonimleştirme →
    randevu satırı duruyor (CANCELLED, silinmedi), kişisel alanlar temiz, favori hard-delete
    edildi; ikinci silme talebi 409 ile reddedilir, ilk talebin zaman damgası sıfırlanmaz;
    anonimleştirilmiş hesapta `cancelDeletion` 409 ile reddedilir; askıdaki işletme sahibi
    APPROVE/REJECT/NO_SHOW yapamaz ama CANCEL hâlâ çalışır.

- Log erişim kontrolü ve saklama süresi: kim (hangi rol) sunucu loglarına erişebilir, loglar
  ne kadar süre tutulur, rotasyon/silme politikası var mı. Faz 3.6'da `PiiMasker` ile
  bilinen call site'lardaki e-posta sızıntısı kapatıldı (bkz. CLAUDE.md karar tablosu,
  "Ham exception mesajı loglama") ama bu, "loglara kim erişebilir" sorusunu cevaplamıyor —
  o soru burada, KVKK kapsamında ele alınmalı.
- **Neden Faz 3'te ama ihmal edilmemeli:** Beta'da gerçek kişisel veri işlemeye başladığın an
  yükümlülük doğar. Ücretsiz olması muaf tutmaz.

### 3.10 — E-posta doğrulama `[BE]` `[SEN]`
Kapalı beta'da (tanıdık, elle seçilmiş işletmeler) şart değil — ama **açık/genel kayıt
başlamadan önce mutlaka olmalı**.

**Neden:** Randevu talebi zaman aşımı kararı (bkz. CLAUDE.md karar tablosu, "Randevu ufku / talep
sınırı") her hesabı işletme başına en fazla 3 açık `PENDING` talep ile sınırlıyor ama bilerek
"tek hesabı sınırlar, çoklu hesabı değil" diyor — çoklu hesap sorununu ayrı bir kalem olarak
Faz 3.5'e (rate limiting) bırakıyordu. Doğrulanmamış e-postayla sınırsız hesap açılabildiği
sürece "bir işletmenin takvimini çok sayıda sahte hesapla doldurma" saldırısının en ucuz yolu
tam olarak budur — IP/rate-limit tek başına yeterli değil, e-postanın gerçek/erişilebilir
olduğunu doğrulamak ek bir maliyet katmanı ekler.

- Kayıt sonrası hesap `emailVerified=false` ile başlar; doğrulama tamamlanmadan randevu talebi
  oluşturulamaz (kapsamın netleşmesi gerekiyor: sadece randevu mu, favori/yorum da mı).
- Doğrulama linki/kodu göndermek bir bildirim kanalı gerektirir — **3.4'e bağımlı**, ondan önce
  başlamaz. Kanal burada zaten e-posta olarak sabit (link göndermenin en ucuz yolu), 3.4'teki
  "kanal kararı ertelendi" notuyla çelişmez çünkü doğrulama e-postası SMS/WhatsApp'tan bağımsız,
  ayrı bir iş.
- Doğrulanmamış hesabın süre aşımı/temizlenmesi (ör. 24 saat sonra pasifleşir mi, silinir mi) —
  karar verilecek.

### 3.11 — Auth sertleştirme: httpOnly cookie + CSRF `[BE]` `[SEN]` ⭐
**Sıralama kesin, "ileride" değil:** 3.7 (konteynerleştirme) bitti → **3.8 (deploy)** → **buraya** →
gerçek işletmelerle beta onboarding. 3.9/3.10'un tamamlanmasına bağımlı değil (paralel
ilerleyebilir), ama beta'ya **gerçek müşteri verisiyle** girmeden önce kesinlikle bitmiş olmalı.

**Neden burada, neden şimdi değil:** JWT şu an `localStorage`'da tutuluyor — XSS ile okunabilir.
httpOnly cookie'ye taşımak bunu kapatıyor (JS `document.cookie` ile erişemiyor), karşılığında
CSRF gündeme geliyor (`SameSite=Lax` + state-changing endpoint'lerde POST zorunluluğu bunu büyük
ölçüde kapatıyor). Bu değişiklik **same-origin mimarisi gerektiriyor** (Faz 3.7'de Caddy ile
zaten kuruldu — cookie'nin `Domain`/`SameSite` davranışını farklı origin'lerde (ör. Vite dev
sunucusu ayrı portta, backend ayrı portta) güvenilir test etmek zordu, artık ikisi de aynı
origin'den servis ediliyor). 3.7'nin ortasında bu ameliyata girmek deploy'a risk eklerdi — ama
gerçek işletmeleri `localStorage`'daki bir JWT ile beta'ya almak da kabul edilebilir değil.
Deploy'dan (3.8) sonra, beta onboarding'den önce yapılacak tek yer burası.

- Backend: login/register yanıtı JWT'yi gövdede değil `Set-Cookie` (httpOnly, `Secure`,
  `SameSite=Lax`) ile döner.
- Frontend: `axios.ts`'teki `localStorage.getItem("token")` + `Authorization` header enjeksiyonu
  kaldırılır, `withCredentials: true` eklenir. Response interceptor'daki 401 mantığı kalır
  (davranış aynı, sadece token'ın nereden geldiği değişiyor).
- CSRF koruması: state-changing (`POST`/`PUT`/`DELETE`) endpoint'lerde bir CSRF token deseni
  (`SameSite=Lax` tek başına yeterli olmayabilir, özellikle GET-tabanlı olmayan cross-site form
  saldırılarına karşı) — tasarım detayı bu maddeye girildiğinde netleşecek.
- Logout akışı: `localStorage.removeItem` yerine backend'in cookie'yi geçersiz kılan bir uç
  sunması gerekiyor (httpOnly cookie'yi JS silemez).

---

# DEPLOYMENT — Öğrenci Bütçesiyle Gerçekçi Plan

## Önerilen kurulum (~5-6 €/ay)

| Bileşen | Seçim | Maliyet | Neden |
|---|---|---|---|
| Sunucu | **Hetzner CX22** (2 vCPU / 4 GB / 40 GB) | ~4,5 €/ay | Bu paranın karşılığında en iyi donanım. Tam kontrol, cold start yok. Alternatif: **Oracle Cloud Always Free** (4 ARM vCPU / 24 GB, gerçekten ücretsiz) — ama kapasite bulmak ve hesap onayı sancılı olabilir. |
| Veritabanı | Aynı sunucuda **Docker Postgres** | 0 | Beta ölçeğinde (5-10 işletme) fazlasıyla yeterli. Yönetilen alternatif Neon/Supabase ücretsiz kotası, ama boşta uyur ve ilk istek yavaşlar. |
| Frontend | **Cloudflare Pages / Vercel** ücretsiz | 0 | Statik build, global CDN, otomatik HTTPS. |
| HTTPS | **Caddy** (otomatik Let's Encrypt) | 0 | Nginx'ten farklı olarak sertifika yenilemesi tamamen otomatik, konfigürasyon 5 satır. |
| Yedek deposu | **Cloudflare R2** (10 GB ücretsiz) veya Backblaze B2 | 0 | Yedeği aynı sunucuda tutmak yedek değildir. |
| Alan adı | `.com` veya `.com.tr` | ~150-400 ₺/yıl | |
| İzleme | **UptimeRobot** ücretsiz (50 monitör) | 0 | `/actuator/health` uç noktasını 5 dk'da bir yoklar, düşerse mail atar. |
| Hata takibi | **Sentry** ücretsiz kota | 0 | |

## Dikkat edilecekler

**Secret yönetimi** — Faz 0.1'de rotasyon yapılacak. Kural: sırlar **asla** repoda durmaz.
Sunucuda `600` izinli `.env` dosyası + `docker compose --env-file`, ya da systemd `EnvironmentFile`.
`SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `SPRING_PROFILES_ACTIVE=prod` ortam değişkeni olarak geçer.

**CORS** — Prod'da `localhost:*` kalıbı kesinlikle kalmamalı. Sadece gerçek frontend domain'i.
Bunun profil bazlı ayrılması gerekir (`application-prod.properties`).

**HTTPS zorunluluğu** — JWT düz HTTP'de gidiyorsa aradaki herkes okur. Caddy ile 80→443
yönlendirmesi + HSTS header. Backend `server.forward-headers-strategy=native` (ters vekil arkasında
doğru şema/IP görmesi için).

**Veritabanı erişimi** — Postgres portu (5432) **dışarıya asla açılmaz**. Docker network içinde
kalır, `ufw` ile 22/80/443 dışında her şey kapalı. SSH'ta parola girişi kapalı, sadece anahtar.

**Yedekleme** — Gecelik `pg_dump | gzip` → R2'ye `rclone` ile. 7 günlük + 4 haftalık saklama.
**Kritik:** Yedeği en az bir kez gerçekten geri yükleyerek test et. Test edilmemiş yedek, yedek değildir.

**Migration disiplini** — Prod'da `ddl-auto=validate`. Şema değişikliği yalnızca Flyway ile.
`create-drop` prod profiline yanlışlıkla sızarsa tüm beta verisi gider.

**Loglama** — Rolling file + `logrotate` (disk dolarsa sunucu durur). PII maskelenmiş.
Merkezi log isterseniz Grafana Cloud ücretsiz kotası (Loki) yeterli.

**Deploy akışı** — Beta ölçeğinde GitHub Actions → SSH → `docker compose pull && up -d` yeterli.
Kubernetes'e ihtiyacın yok ve olmayacak.

**Zaman dilimi** — Sunucu TZ'si UTC'ye sabitlenir, uygulama `Europe/Istanbul` ile çalışır.
Sunucu ve DB farklı TZ'de olursa randevu saatleri kayar; bu tür hatalar canlıda çok geç fark edilir.

---

## Doğrulama

Her faz sonunda:

1. **Yetki testleri (Faz 0'dan itibaren her adımda):** İki farklı işletme sahibi hesabıyla giriş yap,
   birinin token'ıyla diğerinin `businessId`'sine istek at → **403 beklenir**. Postman/`curl`
   koleksiyonu olarak sakla, Faz 3.2'de otomatik teste dönüştür.
2. **Register saldırı testi:** `role: "ADMIN"` ve `id: 2` gönder → ikisi de yok sayılmalı/reddedilmeli.
3. **Slot algoritması:** 0 dakikalık hizmet, kapalı gün, gün sonuna taşan hizmet, arka arkaya
   çakışan randevular → sunucu kilitlenmemeli, doğru slot listesi dönmeli.
4. **Yarış koşulu:** Aynı slota iki eşzamanlı `POST` (`curl` ile paralel) → biri başarılı, biri
   **409 Conflict**.
5. **Yorum kuralı (Faz 2):** PENDING, REJECTED, CANCELLED, NO_SHOW randevulara ve başkasının
   randevusuna yorum denemesi → hepsi reddedilmeli. Aynı randevuya ikinci yorum → DB constraint reddi.
6. **Uçtan uca akış:** `npm run dev` + `mvnw spring-boot:run` ile kayıt → login → işletme seç →
   hizmet → slot → randevu → sahip hesabıyla inbox → onay → tamamlanma → yorum.
7. **Deploy öncesi:** Prod profiliyle yerelde ayağa kaldır, `.env` dosyası olmadan **başlamadığını**
   doğrula (sır sızıntısına karşı fail-fast). Ayrıca tek tek kontrol et: CORS'ta `localhost:*`
   kalmadığını, `DatabaseSeeder`'ın prod profilinde çalışmadığını, frontend build'inin
   `VITE_API_URL` olarak gerçek backend domain'ini kullandığını, storage adaptörü kararının
   verildiğini (3.8) ve bir yedek geri yükleme denemesinin gerçekten yapıldığını (3.8).

---

## İlerleme Takibi

Adımlar sırayla ve tek tek ilerler. Bir adım bitmeden diğerine geçilmez.
Tamamlanan adımın kutusu işaretlenir ve karşısına commit hash'i yazılır.

### Faz 0 — Güvenlik Acil Müdahale
- [x] 0.1 Sırların rotasyonu ve dışarı çıkarılması — c797145
- [x] 0.2 `RegisterRequest` DTO ile mass assignment'ı kapat — d5fa2aa
- [x] 0.3 Global exception handling ve hata sözleşmesi — af6e78a
- [x] 0.4 Method security ve sahiplik kontrolü ⭐ — 0695047
- [x] 0.5 ServiceItem yetkilendirmesi ve servis/işletme eşleşmesi — 065f0ae
- [x] 0.6 Bean Validation — 90671c9
- [x] 0.7 Slot algoritması ve yarış koşulu sertleştirme — 9f6074f, e0e67f1

### Faz 1 — Mimari Temel
- [x] 1.1 Response DTO katmanı — 85d5379
- [x] 1.2 Katman ihlallerini ve SOLID sorunlarını düzelt — e094701
- [x] 1.3 `AvailabilityCalculator` ayrıştırması — 841b18b
- [x] 1.4 Flyway migration + şema sertleştirme — caf4bf8
- [x] 1.5 Business CRUD tamamlama — 69593a7
- [x] 1.6 `WorkingHour` entity — 62d7ff1
- [x] 1.7 Frontend: rol farkındalığı ve ortam yapılandırması — 63b2758
- [x] 1.8 Frontend: işletme paneli iskeleti — 1720059
- [x] 1.9 Frontend: müşteri "Randevularım" ekranı — 6032c27

### Faz 2 — Ürün Vizyonunun Tamamlanması
- [x] 2.1 Randevu durum makinesi + `COMPLETED`/`NO_SHOW` — 868a9cd
- [x] 2.2 Otomatik tamamlama scheduled job — 048b41e
- [x] 2.3 `Staff` entity ve personel CRUD — f2f617e
- [x] 2.4 Slot algoritmasının personel bazlı hale getirilmesi — b606b09
- [x] 2.5 `Appointment.staff` ve çakışma kontrolünün personel bazlı olması — e77998b
- [x] 2.6 `Review` entity ve "sadece gitmiş kişi yorum yapar" garantisi ⭐ — 201e748
- [x] 2.7 İşletme puan ortalaması — 9c100c2
- [x] 2.8 Konuma göre yakın işletme listeleme — def31d5, 33a6433
- [x] 2.9 Backend: personel bazlı slot hesaplama (görünmez) + panelde personel yönetimi — f0357e4, be73a7e
- [x] 2.10 Frontend: yorum/puan ekranı — 8ccc3af
- [x] 2.11 Frontend: konum izni ve "yakınımdakiler" — fcd0c75

### Faz 3 — Üretime Hazırlık
- [x] 3.1 Test altyapısı ve algoritma testleri — 7f33458
- [x] 3.2 Yetkilendirme entegrasyon testleri ⭐ — 7f33458
- [x] 3.3 API dokümantasyonu — 3bd350e
- [x] 3.4 Bildirim altyapısı (kanal-bağımsız) — fe8e547
- [x] 3.5 Rate limiting ve kötüye kullanım koruması — b201bb7
- [x] 3.6 Loglama, izleme ve hata takibi
- [x] 3.7 Konteynerleştirme
- [ ] 3.8a Sunucu kurulumu + sertleştirme + DNS + ilk deploy + doğrulama
- [ ] 3.8b Yedekleme + restore provası + izleme (3.8a bitmeden başlanmaz)
- [ ] 3.9 KVKK ve hukuki metinler
- [ ] 3.10 E-posta doğrulama (3.4'e bağımlı, açık kayıt öncesi şart)
- [ ] 3.11 Auth sertleştirme: httpOnly cookie + CSRF ⭐ (3.8'den sonra, beta onboarding'den önce)

---

## Bekleyen Kararlar

| Karar | Ne zaman verilecek | Neden bekliyor |
|---|---|---|
| Bildirim kanalı (SMS / e-posta / WhatsApp / in-app) | Beta sahaya inmeden önce | Gerçek işletmelerle konuşulmadan kanal seçmek erken. Faz 3.4'teki `NotificationPort` soyutlaması kararı maliyetsiz erteliyor. |
| Beta şehri ve pilot işletmeler | Beta öncesi | |
| Abonelik fiyatlandırma modeli | Beta geri bildiriminden sonra | Ücretsiz beta bittiğinde. |
| Süper Admin paneli kapsamı | Faz 3 sonrası | Şu an rol enum'ında `ADMIN` var ama süper admin ayrı bir rol olarak modellenmedi. |
