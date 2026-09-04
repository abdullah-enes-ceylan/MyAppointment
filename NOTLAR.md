# NOTLAR.md — Randevum

Bu dosya **yapılacak iş listesi değil** (o [ROADMAP.md](ROADMAP.md)'de) ve **agent çalışma
kuralı da değil** (o [CLAUDE.md](CLAUDE.md)'de). Buraya sadece **sonradan hatırlanması
gereken bilgiler** girer: verilen kararlar ve gerekçeleri, dikkat edilmesi gereken tuzaklar,
ortam/altyapı bilgileri, bilinen (henüz çözülmemiş) sorunlar. Kronolojik değil, konu bazlı
tutulur — eskiyen/geçersiz kalan notlar güncellenip silinir.

---

## Verilen Kararlar ve Gerekçeleri

**(2026-09-04) Randevu alma sayfasında ödeme/depozito bilgisi ve "İşletmeye Mesaj Gönder"
özelliği eklenmedi.** Google AI Studio prototipinde ikisi de vardı. Gerekçe: kullanıcı
onayı — şu an gereksiz görüldü, ileride talep gelirse ayrıca ele alınır.

**(2026-09-04) Randevu alma sayfasındaki "İletişim Bilgileri" salt-okunur bir özet kart
olarak kaldı, düzenlenebilir form değil; "Özel Not" alanı hiç eklenmedi.** Ad/Telefon zaten
hesaptan/token'dan geliyor, backend'e ayrıca göndermeye gerek yok. Not alanının backend'de
hiçbir karşılığı yok (`AppointmentRequest`'te böyle bir alan yok) — eklemek kapsamı
büyütüyor, bu yüzden ayrı bir iş kalemi olarak ROADMAP 3.13'e yazıldı, o an kod yazılmadı.

**(2026-09-04) HomePage kartlarında AI Studio'nun "3 tıklanabilir hızlı saat" özelliği
bilinçli olarak alınmadı**, tek "Bugün En Erken" rozeti + "Randevu Al" butonu kullanıldı.
Gerekçe: her kart zaten 1 `available-slots` isteği atıyor, 3'e çıkarmak rate limit riskini
3 katına çıkarırdı. **Bu öngörü doğru çıktı ama yetersiz kaldı** — tek istekle bile risk
gerçekleşti, bkz. aşağıdaki "rate limit tuzağı".

**(2026-09-04) `/business/:id` (randevu alma) sayfasında navbar'daki konum seçme pill'i
gizlendi.** Kullanıcı zaten belirli bir işletmeye bakıyorken konum seçmenin bir anlamı yok.

**(2026-09-04) İşletme "Randevu Durumu" rozeti: `autoApprove=true` → "Anında Onaylı",
`autoApprove=false` → "İşletme Onayından Geçer".** İlk yazımda yanlışlıkla "Onay Bekliyor"
yazılmıştı (kullanıcı düzeltti) — "Onay Bekliyor" bir randevunun PENDING durumunu çağrıştırıyor,
oysa bu rozet henüz randevu talebi bile oluşturulmadan, işletmenin GENEL politikasını
gösteriyor; "İşletme Onayından Geçer" bu ayrımı daha doğru anlatıyor.

**(2026-09-04) Randevu alınabilmesi için minimum "şu andan itibaren pay": 15 dakika**
(`app.appointment.minimum-booking-lead-time`, `AppointmentPolicyProperties`). Değer
`AvailabilityCalculator`'ın ızgara adımıyla (varsayılan 15dk) bilerek aynı büyüklükte —
"bir sonraki ızgara adımından itibaren alınabilir" diye tek cümleyle anlatılabiliyor.
**Hem `getAvailableTimeSlots` (liste) hem `createAppointment` (oluşturma) AYNI kaynaktan**
(`AppointmentExpiryPolicy.getMinimumBookingLeadTime()`) okuyor — ikisi arasında sessiz bir
ayrışma riski yapısal olarak kapatıldı, kural iki yerde ayrı ayrı tanımlı DEĞİL.

**(2026-09-04) "Bugün"/"şu an" karşılaştırmaları `LocalDateTime` üzerinden yapılıyor,
çıplak `LocalTime` değil.** `LocalTime.plusMinutes` gece yarısını sarar (23:50 + 15dk →
00:05), bu da sabahın erken saatlerini yanlışlıkla "henüz gelmedi" sanmaya yol açardı.
`date.atTime(slot)` ile mutlak bir an kurup mutlak bir cutoff'la kıyaslamak bu sarmayı
yapısal olarak imkansız kılıyor (bkz. `AppointmentService.excludePastSlotsForToday`).

**(2026-09-04) İşletme kapak fotoğrafı depolaması yerel diskten Cloudflare R2'ye
taşınıyor, 3.8 (deploy) öncesinde.** Gerekçe: henüz gerçek prod verisi yok, yani şimdi
yapılırsa "veri göçü" diye ayrı bir iş hiç doğmuyor; deploy sonrasına bırakılırsa hem
kalıcı disk riskiyle (CLAUDE.md karar tablosu) uğraşılır hem de sonradan taşıma yazılır.
Kilit kararlar (Claude Code + bir başka Claude oturumunun çapraz incelemesiyle):
- **Yazma backend'den geçer, okuma doğrudan CDN'den** (`cdn.randevumweb.com`, R2 custom
  domain) — presigned URL ile tarayıcıdan direkt yükleme YOK, çünkü sunucuya inmeyen bir
  dosyanın magic-byte doğrulaması yapılamaz.
- **Config-flag deseni:** `app.business-photo.storage-provider` (`BusinessPhotoStorageProvider`
  enum, `LOCAL`/`R2`, varsayılan `LOCAL`) — `AppointmentPolicyProperties`'teki "geçersiz
  değerde güvenli varsayılana düş" deseninin BİLEREK DIŞINDA: bu alan enum, geçersiz bir
  değer Spring'in binding'i tarafından **açılışta hata verilerek** yakalanır (canlı
  doğrulandı: `storage-provider=bogus` ile `APPLICATION FAILED TO START` +
  `No enum constant ...bogus`). Sebebi: sessizce `local`'e düşmek, prod'da fark edilmeden
  fotoğrafların container diskine yazılması gibi tam da kaçınılmak istenen senaryoyu
  üretirdi. Prod'da `storage-provider=r2` **açıkça** yazılacak, varsayılana güvenilmeyecek.
- **`R2StorageProperties`'e asla `@ToString`/`@Data` eklenmesin** — `secretAccessKey`
  alanı taşıyor, projenin mevcut DTO deseni zaten sadece `@Getter`/`@Setter` kullanıyor
  (bkz. `LoginRequest`), burada bu ayrıca güvenlik gerekçesiyle zorunlu.
- **`{uuid}-card.jpg`/`{uuid}-detail.jpg` adlandırması R2'de birebir korunacak.**
- **`read()` metodu R2 implementasyonunda da gerçek çalışacak** (Liskov ihlali olmasın
  diye `UnsupportedOperationException` atılmayacak), ama `GET /api/business-photos/**`
  ucu SADECE `storage-provider=local` iken yayınlanacak — aksi halde biri CDN yerine bu
  ucu kullanıp VPS bandwidth'ini/R2 class B operasyonlarını tüketebilir, edge cache
  tamamen atlanır.
- **Backup için ek bir strateji kurulmayacak** — tek VPS diskinden R2'ye geçmek zaten
  net bir iyileşme (dağıtık depolama), ek yedekleme kuruluncaya kadar beklemek anlamsız.
  R2 versiyonlama/lifecycle değerlendirmesi ayrı bir ROADMAP maddesi (bkz. ROADMAP.md).
- **Custom domain R2 panelinden bağlanacak, elle DNS kaydı AÇILMAYACAK** — R2, custom
  domain bağlarken CNAME'i kendisi oluşturuyor; elle önden açılırsa çakışma çıkar. Kurulum
  sonrası apex/`www` DNS-only (gri) kalmalı, sadece `cdn` proxied (turuncu) olmalı — bkz.
  aşağıdaki "Cloudflare proxy" notu, bu ayrı bir subdomain olduğu için mevcut kararla
  ÇELİŞMİYOR.
- **Bucket CORS'a gerek yok** — görseller `<img src>` ile çekiliyor, tarayıcıdan doğrudan
  upload yok, CORS hiç devreye girmiyor.

**(2026-09-04) WebP'ye geçiş (görsel çıktı formatı) R2 taşımasından AYRI, sonraya
bırakıldı.** Sebep: JDK'nın yerleşik `ImageIO`'sunda (Thumbnailator'ın dayandığı) hiç
WebP yazıcısı yok — `outputFormat("webp")` "uygun writer bulunamadı" ile patlar. Aday
çözüm `org.sejda.imageio:webp-imageio` (native/JNI, glibc-Linux için) — bizim
`backend/Dockerfile`'ın ZATEN `eclipse-temurin:21-jre-jammy` (Alpine DEĞİL, glibc)
kullanması bu kütüphaneyle uyumluluğu muhtemel kılıyor (Dockerfile'daki gerekçe: geçmişte
JPEG için de aynı native-codec sorunu yaşanmıştı). **Ama bu doğrulanmadı** — R4 başlarsa
ilk adım, resize kodunu hiç yazmadan önce, image içinde gerçek bir WebP yazma denemesi
olacak. Sonuç olumsuzsa WebP tamamen ayrı bir araştırma kalemi olur, R2 ile hiç
karıştırılmaz.

---

## Dikkat Edilmesi Gereken Tuzaklar

**Rate limit tuzağı (canlı bulundu).** HomePage'deki her işletme kartı, "Bugün En Erken"
rozeti için ayrı bir `GET /api/appointments/available-slots` isteği atıyor. Birkaç
kategori/işletme detayı gezmek, genel rate limit tavanını (IP başına dakikada 300,
`RateLimitFilter`) hızla dolduruyor. Tavan dolunca `/api/businesses` de 429 dönüyor.
**Eğer ileride "hiçbir işletme görünmüyor" / "kategori boş görünüyor" şikayeti gelirse
önce rate limit'e bakılmalı**, gerçekten boş bir kategori sanılmamalı — özellikle yerel
geliştirmede hızlı art arda sayfa gezmek bunu kolayca tetikliyor. Yanlış "kategoride
işletme yok" mesajı düzeltildi (artık gerçek hata + "Tekrar Dene" gösteriliyor), ama asıl
istek hacmi hâlâ fazla — kalıcı çözüm ROADMAP 3.14'te, henüz yapılmadı.

**Zamana bağlı davranışı canlı `curl` ile kanıtlamak günün saatine bağlı olarak imkânsız
olabilir.** Örnek: "15 dakika içindeki randevu reddedilir" kuralını gece 04:50 gibi bir
saatte canlı denemek, işletmenin henüz açık olmamasından dolayı FARKLI bir hataya (çalışma
saatleri dışı) takılırdı — asıl kuralı hiç kanıtlamazdı. Bu tür durumlarda sabit `Clock`
enjekte eden bir test (`AccountDeletionResponseFieldsTest`'teki `@TestConfiguration` +
`@Primary Clock` deseni) asıl kanıt kaynağı olmalı; canlı `curl` sadece zamanlama uygunsa
ek doğrulama olarak yapılır, "canlı deneyemedim" diye özelliği kanıtsız bırakmanın gerekçesi
olmamalı.

**Java record DTO'lara (`BusinessResponse`/`BusinessDetailResponse`) yeni alan eklemek**
tüm constructor çağrılarını etkiler gibi görünse de, bu iki DTO SADECE
`BusinessMapper.toResponse`/`toDetailResponse` içinde kuruluyor — tek çağrı noktası, blast
radius sanıldığı kadar büyük değil. Asıl değişiklik `BusinessController`'daki liste
endpoint'lerinin "her işletmeyi bağımsız `.map(...)`'leme" akışını "önce tüm listeyi topla,
sonra toplu hesapla" şekline çevirmek olacak (bkz. ROADMAP 3.14).

**Mockito `@Mock`'lu bir alanı SADECE bazı testlerde stub'lamak gerekiyorsa, ortak
`@BeforeEach`'e KOYMA** — strict stubs varsayılanıyla `UnnecessaryStubbingException` fırlatır
(kullanılmayan stub, testi PATLATIR). Sadece ihtiyaç duyan testin kendi içine taşı.

**Yeni bir randevu-oluşturma yolu eklenirse** (ör. ileride bir "hızlı yeniden randevu al"
özelliği, ya da personel panelinden manuel randevu girişi), `expiryPolicy.getMinimumBookingLeadTime()`
kontrolünü o yolun da kendi içinde ÇAĞIRMASI gerekiyor — kural merkezi bir yerde TANIMLI
ama merkezi bir yerden OTOMATİK uygulanmıyor, her create-yolu bunu bilerek eklemeli.

---

## Ortam / Altyapı Bilgileri

**Yerel dev seed kullanıcısı:** `ahmet@test.com` / `123456` (`DatabaseSeeder.java`, sadece
`dev` profili aktifken çalışır, prod'da hiç tetiklenmez).

**Dev backend:** `http://localhost:8080`, `dev` profili, yerel Postgres (`appointment_db`).
**Dev frontend:** `http://localhost:5173` (Vite) — `/api` ve `/actuator`'ı backend'e proxy'liyor
(bkz. CLAUDE.md'deki same-origin kararı), ayrı bir `VITE_API_URL` yok.

**Backend kod değişikliği sonrası hot-reload YOK** (devtools kurulu değil) — her Java
değişikliğinden sonra süreç manuel yeniden başlatılmalı: `cd backend && ./mvnw spring-boot:run`
(gerekirse `-Dspring-boot.run.profiles=dev` ile).

**Cloudflare R2 bucket:** `randevum-storage` (Standard storage class, Automatic/Eastern
Europe location, Public Access disabled — herkese açık okuma custom domain ile ayrıca
açılacak). API token **Account API Token**, "Object Read & Write", sadece bu bucket'a
scope'lu, **TTL 1 yıl** (2026-09-04'te oluşturuldu) — **2027-09-04 civarında yenilenmesi
gerekiyor**, süresi dolarsa yeni fotoğraf yükleme/silme sessizce 401/403 almaya başlar
(mevcut fotoğrafların CDN'den servis edilmesini ETKİLEMEZ, o ayrı bir erişim yolu).
Access Key ID/Secret Access Key hiçbir dosyaya (git'e giren hiçbir yere) yazılmadı —
sadece geliştiricinin kendi güvenli notunda; koda bağlanınca `application-dev.properties`
(gitignore'da) içine girecek.

---

## Bilinen Sorunlar (Çözülmemiş)

**HomePage'in kart başına attığı "Bugün En Erken" isteği hâlâ N+1 (istek başına, HTTP
seviyesinde).** Sadece yanlış hata mesajı giderildi (bkz. yukarıdaki rate limit tuzağı),
asıl istek hacmi azaltılmadı. Detaylı toplu-hesaplama planı: ROADMAP 3.14.

**Puan ortalaması hesaplaması hâlâ işletme başına 1 sorgu (N+1), SQL seviyesinde.**
`BusinessController.toResponseWithRating`/`toDetailResponseWithRating` — beta ölçeğinde
bilinçli olarak dokunulmadı (kod içindeki "Faz 2.7" yorumunda gerekçesi var). ROADMAP
3.14 uygulanırken aynı toplu (`IN` clause) yaklaşımla bir alt adım olarak çözülebilir.
