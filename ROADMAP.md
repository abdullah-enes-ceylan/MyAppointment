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

### Frontend (Faz 2)
- **2.9** Randevu akışına personel seçimi adımı `[AI]`
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

### 3.5 — Rate limiting ve kötüye kullanım koruması `[BE]` `[AI]`
- Login'de brute force koruması (Bucket4j veya basit in-memory sayaç + IP/email bazlı)
- `permitAll` olan `/available-slots` ve `/register` uçlarına hız limiti (kimlik doğrulamasız ve DB-yoğun)

### 3.6 — Loglama, izleme ve hata takibi `[BE]` `[AI]`
- `logback-spring.xml`: JSON formatı, rolling file, **PII maskeleme** (telefon/email loglara düşmesin)
- Spring Boot Actuator: `/health` açık, diğer endpoint'ler kapalı/korumalı
- Sentry (ücretsiz kota: ~5k olay/ay) ile exception takibi

### 3.7 — Konteynerleştirme `[DevOps]` `[AI]`
- Backend `Dockerfile` (multi-stage, JRE slim, non-root kullanıcı)
- `docker-compose.yml`: app + postgres + Caddy
- Frontend static build → Caddy veya Cloudflare Pages

### 3.8 — Deploy, yedekleme, izleme `[DevOps]` `[SEN]`
Detaylar aşağıdaki bölümde.

### 3.9 — KVKK ve hukuki metinler `[SEN]`
Gerçek kişilerin ad, telefon ve randevu geçmişini işleyeceksin.
- Aydınlatma metni, açık rıza akışı, gizlilik politikası, kullanım şartları
- VERBİS kayıt yükümlülüğü eşiğini kontrol et
- İşletmelerle veri işleyen sözleşmesi (sen veri sorumlususun, işletme de öyle)
- Hesap ve veri silme akışı (unutulma hakkı) — teknik olarak da uygulanmalı
- **Neden Faz 3'te ama ihmal edilmemeli:** Beta'da gerçek kişisel veri işlemeye başladığın an
  yükümlülük doğar. Ücretsiz olması muaf tutmaz.

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
   doğrula (sır sızıntısına karşı fail-fast).

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
- [ ] 0.7 Slot algoritması ve yarış koşulu sertleştirme

### Faz 1 — Mimari Temel
- [ ] 1.1 Response DTO katmanı
- [ ] 1.2 Katman ihlallerini ve SOLID sorunlarını düzelt
- [ ] 1.3 `AvailabilityCalculator` ayrıştırması
- [ ] 1.4 Flyway migration + şema sertleştirme
- [ ] 1.5 Business CRUD tamamlama
- [ ] 1.6 `WorkingHour` entity
- [ ] 1.7 Frontend: rol farkındalığı ve ortam yapılandırması
- [ ] 1.8 Frontend: işletme paneli iskeleti
- [ ] 1.9 Frontend: müşteri "Randevularım" ekranı

### Faz 2 — Ürün Vizyonunun Tamamlanması
- [ ] 2.1 Randevu durum makinesi + `COMPLETED`/`NO_SHOW`
- [ ] 2.2 Otomatik tamamlama scheduled job
- [ ] 2.3 `Staff` entity ve personel CRUD
- [ ] 2.4 Slot algoritmasının personel bazlı hale getirilmesi
- [ ] 2.5 `Appointment.staff` ve çakışma kontrolünün personel bazlı olması
- [ ] 2.6 `Review` entity ve "sadece gitmiş kişi yorum yapar" garantisi ⭐
- [ ] 2.7 İşletme puan ortalaması
- [ ] 2.8 Konuma göre yakın işletme listeleme
- [ ] 2.9 Frontend: personel seçimi
- [ ] 2.10 Frontend: yorum/puan ekranı
- [ ] 2.11 Frontend: konum izni ve "yakınımdakiler"

### Faz 3 — Üretime Hazırlık
- [ ] 3.1 Test altyapısı ve algoritma testleri
- [ ] 3.2 Yetkilendirme entegrasyon testleri ⭐
- [ ] 3.3 API dokümantasyonu
- [ ] 3.4 Bildirim altyapısı (kanal-bağımsız)
- [ ] 3.5 Rate limiting ve kötüye kullanım koruması
- [ ] 3.6 Loglama, izleme ve hata takibi
- [ ] 3.7 Konteynerleştirme
- [ ] 3.8 Deploy, yedekleme, izleme
- [ ] 3.9 KVKK ve hukuki metinler

---

## Bekleyen Kararlar

| Karar | Ne zaman verilecek | Neden bekliyor |
|---|---|---|
| Bildirim kanalı (SMS / e-posta / WhatsApp / in-app) | Beta sahaya inmeden önce | Gerçek işletmelerle konuşulmadan kanal seçmek erken. Faz 3.4'teki `NotificationPort` soyutlaması kararı maliyetsiz erteliyor. |
| Beta şehri ve pilot işletmeler | Beta öncesi | |
| Abonelik fiyatlandırma modeli | Beta geri bildiriminden sonra | Ücretsiz beta bittiğinde. |
| Süper Admin paneli kapsamı | Faz 3 sonrası | Şu an rol enum'ında `ADMIN` var ama süper admin ayrı bir rol olarak modellenmedi. |
