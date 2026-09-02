# CLAUDE.md — Randevum

Bu dosya, bu projede çalışan her AI oturumu için bağlam ve çalışma kurallarını içerir.
Yol haritası ve teknik durum tespiti için: [ROADMAP.md](ROADMAP.md)

---

## Geliştirici Hakkında

Üçüncü sınıf yazılım mühendisliği öğrencisi, Java/Spring Boot backend üzerine yoğunlaşıyor.
Bu proje hem portfolyo hem de ticari ürün adayı. **Amaç sadece çalışan bir uygulama çıkarmak
değil, her şeyin neden öyle yapıldığını öğrenerek ilerlemek.**

---

## Proje: Çoklu İşletme Randevu ve Yönetim Sistemi (SaaS)

Berber, kuaför, güzellik salonu, spa, dövme stüdyosu, diyetisyen gibi randevuyla çalışan yerel
işletmelerin WhatsApp/defter-kalem ile yürüttüğü randevu sürecini dijitalleştiren merkezi bir
SaaS platformu.

### Final vizyon — kullanıcı akışı

Randevu almak isteyen kişi uygulamaya girer → kategori filtresinden (ör. berber) seçim yapar →
konumuna yakın işletmeler listelenir → her işletmenin 5 yıldız üzerinden puanı ve yorumları
görünür → işletmeyi seçer, hizmetleri (süre + fiyat) görür → hizmet seçip müsait saatlerden
randevu talebi oluşturur → randevu sonrası puan ve yorum bırakabilir.
**Sadece gerçekten o randevuya gitmiş kişiler yorum ve puan verebilir.**

### Roller ve yetkiler

- **`USER` (müşteri):** İşletme ve hizmetleri listeler, uygun saatlere randevu talebi (`PENDING`)
  oluşturur, tamamlanmış randevusuna yorum/puan bırakır.
- **`BUSINESS_OWNER` / `ADMIN`:** Sadece **kendi** işletmesinin "İstek Kutusu (Inbox)" paneline
  erişir. Gelen talepleri görür, `APPROVED` veya `REJECTED` yapar. Kendi hizmetlerini, çalışma
  saatlerini ve fiyatlarını yönetir.
- **Süper Admin:** Şimdilik yok, ileride eklenecek (tüm sistemi, işletmeleri, abonelikleri yönetir).

### Ticari plan

Sistem canlıya alındıktan sonra 5-10 yerel işletmede 1-2 ay ücretsiz beta yapılacak, gerçek saha
geri bildirimiyle (ör. "müşteri gelmedi butonu lazım", "aynı saate 2 kişi alabilmeliyim") sistem
olgunlaştırılacak, sonrasında aylık abonelik modeline geçilecek.
**Bu yüzden multi-tenant izolasyonu ve veri güvenliği baştan doğru kurulmalı.**

---

## Teknik Altyapı

**Backend:** Java 21, Spring Boot, RESTful API, JPA/Hibernate, Lombok, katmanlı mimari
(entity / repository / service / controller), paket kökü `com.randevu.backend`.

**Veritabanı & güvenlik:** PostgreSQL, Spring Security, JWT tabanlı kimlik doğrulama,
rol bazlı yetkilendirme (RBAC), BCrypt ile parola hashleme.

**Frontend:** React 19 + Vite + Tailwind CSS 4. **Açık tema, lacivert (`#161b33`) marka rengi**
(eski koyu tema + zümrüt yeşili bırakıldı — müşteri ekranları yeniden tasarlandı; işletme
paneli sekmeleri hâlâ eski koyu temada, kademeli geçecek). Axios ile API haberleşmesi,
protected route yapısı, JWT'deki role göre reaktif navigasyon, mobilde alt sekme çubuğu.

### Komutlar

```bash
cd backend && ./mvnw spring-boot:run
```

```bash
cd frontend && npm run dev
```

### Yerel ortam — tekrar eden tuzaklar

Bunlar birkaç kez zaman kaybettirdi, her seferinde yeniden keşfetmeye gerek yok.

**Türkçe karakter + Git Bash.** Kabuktan doğrudan Türkçe karakterli SQL veya JSON
göndermek veriyi bozuyor (`invalid byte sequence for encoding "UTF8"`, ya da API
tarafında `JSON parse error: Invalid UTF-8 middle byte`). Dosyayı Python ile UTF-8
yazıp göndermek gerekiyor:

```bash
python3 -c "import io; io.open('q.sql','w',encoding='utf-8').write('...')"
PGCLIENTENCODING=UTF8 psql ... -f q.sql
curl ... -H "Content-Type: application/json; charset=utf-8" --data-binary @govde.json
```

**psql yolu:** `C:\PostgreSQL\18\bin\psql.exe` (Program Files altında **değil**).
Veritabanı `appointment_db`. Kimlik bilgileri `application-dev.properties`'te —
o dosya gitignore'da, **buraya asla yazma**.

**Şema doğrulaması.** `ddl-auto=validate` açık: entity'ye alan eklenip migration
yazılmazsa (veya migration henüz uygulanmadıysa) uygulama açılmaz ve testler
`BackendApplicationTests` üzerinden düşer. Bu bir arıza değil, güvenlik ağı —
migration'ı uygulamak için backend'i yeniden başlat.

**Uygulanmış migration dosyası ASLA düzenlenmez.** Flyway açılışta checksum
doğruluyor; tek bir yorum satırı değişikliği bile `FlywayValidateException` ile
uygulamayı durdurur. Yeni numaralı migration yaz.

---

## Verilmiş Mimari Kararlar

Bu kararlar tartışılıp verildi; yeniden açmadan önce sor.

| Konu | Karar |
|---|---|
| Tenant modeli | Bir sahip **N işletme** yönetebilir. `businessId` **asla JWT'ye gömülmez**; her istekte sahiplik DB'den doğrulanır. |
| Personel/kapasite | Faz 2'de tam `Staff` modeli. "Aynı saate 2 kişi" ihtiyacının çözümü budur, basit kapasite alanı değil. |
| Personel seçimi (müşteri) | **(2026-08-23)** Müşteri randevu alırken personel seçmez/görmez. Sistem "en az dolu personele ata" kuralıyla görünmez şekilde atar. Gerekçe: küçük işletmede müşteri personeli denemeden değerlendiremez, işletme de dengesiz yoğunluk/favoritizm istemez. Product ihtiyacı çıkarsa ayrı adım olarak eklenir — bkz. ROADMAP 2.9. |
| Bildirim kanalı | **Karar ertelendi.** Faz 3'te kanal-bağımsız `NotificationPort` soyutlaması kurulur; kanal seçilince tek adapter eklenir. |
| Para tipi | `BigDecimal(10,2)` — `double` değil. |
| Zaman | `LocalDateTime` + `Europe/Istanbul`. **Tek saat kaynağı: `TimeConfig`'teki `Clock` bean'i.** Kodda çıplak `LocalDateTime.now()` / `Instant.now()` **yasak** — hepsi enjekte edilen `Clock`'tan geçer. Gerekçe: tüm zaman kolonları `timestamp without time zone`, yani DB hiçbir dönüşüm yapmıyor; o değerin hangi dilimi ifade ettiğine dair tek otorite uygulama. **(2026-09-01, Faz 3.8 planlaması)** Container'ın işletim sistemi saati (Docker base image'ları varsayılan UTC) bunu ETKİLEMİYOR — canlı, ayırt edici bir testle kanıtlandı: container OS UTC'yken (saat ~14:09 UTC), "bugün 15:30" için randevu denendi — ham UTC mantığıyla bu gelecekte görünürdü (kabul edilmeliydi), ama backend "geçmişte olamaz" diyerek reddetti çünkü gerçek Istanbul saati zaten 17:09'du. `TimeConfig`'in hem `Clock` bean'i hem `TimeZone.setDefault(...)` çağrısı container TZ'sinden bağımsız çalışıyor — container'a ayrıca `TZ=Europe/Istanbul` eklemeye gerek yok. Frontend tarafı (`new Date(dateStr)` + `.getHours()`, TZ'siz bir `LocalDateTime` string'i üzerinde) mantıken de güvenli (parse ve okuma aynı çalıştırma ortamının yerel saatini kullandığı için round-trip kimlik dönüşümü) ama bu iddia farklı cihaz saat dilimleri için AMPİRİK olarak test EDİLEMEDİ — Windows'ta Node, `TZ` ortam değişkenini yok sayıyor (Docker Desktop izin sorunuyla aynı sınıf bir yerel test kısıtı). |
| Konum sorgusu | Bounding box ön filtresi + Haversine. PostGIS değil (ucuz hosting'de bakım yükü). |
| Puan ortalaması | Önce aggregate sorgu; denormalizasyon ancak ölçek gerektirince. |
| Kategori vs. hizmet grubu | **(2026-08-28)** `BARBER` kategorisi **kaldırıldı** (V10). "Berber" ile "Kuaför" aynı düzlemde değildi — berber sadece erkeğe hizmet veren bir kuaför. Kategori "ne hizmeti", `ServedGender` (ERKEK/KADIN/UNISEX) "kime" sorusunu cevaplıyor. Filtrede "Erkek" seçilince UNISEX işletmeler **de** çıkar. |
| Randevu istek mi, direkt mi | **(2026-08-28)** Varsayılan **istek modu** (İstek Kutusu) kalıyor — beta'da asıl risk benimsenme, esnafa "sen onaylamadan hiçbir şey olmaz" diyebilmek önemli. İşletme başına **"otomatik onay" anahtarı** eklenecek (henüz yapılmadı); onay adımının değeri zamanla azalıyor. |
| Talep zaman aşımı | **(2026-08-28)** `düşme anı = randevu saati − min(sabitPay, pencere × oran)`, varsayılan 1 saat / %10. Tek formül, sınırda sıçrama yok. Sadece sabit pay olsa 30 dk sonrasına alınan randevu **doğduğu anda** düşerdi; sadece oran olsa keyfi saatler çıkar ve tek cümleyle anlatılamazdı. Kayıt **silinmiyor**, `EXPIRED` oluyor (müşteriye mesaj gösterilebilsin + uyuşmazlıkta kanıt). `REJECTED` kullanılmıyor: "işletme reddetti" yanlış bilgi olurdu. |
| Randevu ufku / talep sınırı | **(2026-08-28)** En fazla **90 gün** ileriye randevu (öncesinde hiç sınır yoktu). Aynı işletmede en fazla **3 açık `PENDING`** talep — işletme bazında, çünkü saldırı "bir işletmenin takvimini doldurmak"; genel sınır normal kullanıcıyı cezalandırırdı. `APPROVED` sayılmaz (düzenli müşteri bir sonraki randevusunu alabilmeli). Tek hesabı sınırlar, çoklu hesabı değil (o Faz 3.5). |
| İş kuralı sayıları | **Asla koda gömülmez.** `application.properties` + tipli `@ConfigurationProperties` (bkz. `AppointmentPolicyProperties`). Geçersiz değerde exception değil, güvenli varsayılana düşüp uyarı loglanır (`AvailabilityCalculator.effectiveGranularity` deseni). |
| İşletme kapak fotoğrafı depolama | **(2026-08-30)** Yerel diske yazılıyor (`BusinessPhotoStorage` arayüzü arkasında, bkz. `LocalDiskBusinessPhotoStorage`). **Bilinçli kabul edilen risk:** deploy ortamının dosya sistemi kalıcı olmayan bir platform olursa (ör. bazı PaaS'lerde ephemeral disk) tüm işletme fotoğrafları sessizce kaybolur — uygulama hata vermez, `photo_key` DB'de dururken dosyalar diskten gider, kapak görseli aniden gradyana döner. Arayüz sayesinde ucuz bir karar: S3/R2'ye geçiş tek bir implementasyon + bean değişikliği. **Deploy platformu seçilirken bu göz önünde bulundurulmalı** (kalıcı disk garantisi yoksa S3/R2'ye geçmeden prod'a alınmamalı). **(2026-09-01 güncelleme)** docker-compose'daki depolama **bind mount olarak başlayıp named volume'a çevrildi** — bind mount'ta taze bir Linux sunucuda host klasörü Docker tarafından `root:root` oluşturulup `appuser` (UID 1000) yazamıyordu; bu, Postgres 5432 yanılgısıyla BİREBİR AYNI desende Windows/Docker Desktop'ta hiç görünmüyordu (host izinleri container'a gerçek yansımıyor). Named volume, image'da zaten `chown`'lanmış mount yolundan sahipliği otomatik devraldığı için (canlı doğrulandı) bu sınıf hatayı tamamen ortadan kaldırıyor — bedeli, veriye artık `cd`/`rsync` ile değil bir yardımcı container üzerinden erişilmesi. İçerik doğrulaması (`BusinessPhotoService`) istemcinin Content-Type/uzantı iddiasına hiç bakmıyor, `ImageIO`'nun kendi baytlara baktığı magic-byte kontrolü + her yüklemenin JPEG'e yeniden kodlanması sayesinde SVG/HTML tabanlı stored-XSS zaten kapalı — gerçek `<script>` içeren bir SVG hem kendi uzantısıyla hem `.jpg` kılığında canlı denenip ikisi de reddedildi. |
| Docker build context = `src/main/resources`'ın tamamı | **(2026-08-31)** `Dockerfile`'ın build aşaması `COPY src ./src` yapıyor, Maven de `src/main/resources` altındaki HER dosyayı (uzantısı `.example` olsa bile) `target/classes`'e, oradan da jar'a kopyalıyor — `.dockerignore` bunu etkilemez, o sadece build context'e giren dosyaları süzer, jar'ın İÇİNE neyin gireceğini değil. Somut örnek: `application-dev.properties.example` (şablon, sadece `BURAYA_...` placeholder'ları var) image'a giriyor — bugün zararsız çünkü içi boş, ama biri o dosyayı yerelde doldurup **yanlışlıkla gerçek bir değerle kaydederse** (placeholder'ı silip kendi şifresini yazıp commit etmeyi unutmak gibi) bu sessizce her build'e gömülür. **`src/main/resources` altındaki hiçbir dosyaya gerçek sır/şifre/token yazılmamalı** — sadece `application-dev.properties` (gitignore'da, `.dockerignore`'da da hariç tutulan gerçek dosya) taşıyabilir onları. |
| Frontend/backend origin mimarisi | **(2026-09-01)** Prod'da Caddy ikisini de AYNI origin'den sunuyor (frontend static build + backend'e `/api`/`/actuator` reverse proxy) — Cloudflare Pages gibi ayrı bir platform DEĞİL, gerekçe: aynı origin CORS'u tamamen gereksiz kılıyor (deployed frontend için), rate limiting'in gerçek IP görmesi zaten Caddy'ye bağlıydı. Dev'de de AYNI model — `vite.config.js`'teki `server.proxy` ile Vite dev sunucusu (5173) `/api`/`/actuator`'ı backend'e (8080) yönlendiriyor. Bu yüzden `VITE_API_URL` gibi ayrı bir taban-URL env değişkeni **tamamen kaldırıldı** — `axios.ts`'te `baseURL` hiç set edilmiyor, `utils/photo.ts`'teki `resolvePhotoUrl` artık no-op. Yeni bir ortam (ör. bir mobil uygulama, ayrı bir admin paneli) GERÇEKTEN farklı bir origin'den API'ye erişecekse, o zaman `CORS_ALLOWED_ORIGINS`'in devam eden fail-fast mekanizması (bilerek kaldırılmadı) devreye girecek. |
| JWT saklama yeri: localStorage → httpOnly cookie | **(2026-09-01) Karar verildi, ertelenmedi — sıralandı.** JWT şu an `localStorage`'da (XSS ile okunabilir). httpOnly cookie'ye geçiş **Faz 3.7'nin same-origin mimarisini gerektiriyordu** (farklı origin'lerde `SameSite`/`Domain` davranışını güvenilir test etmek zordu) — o artık kuruldu. Sıra: **3.7 (tamam) → 3.8 deploy → 3.11 auth sertleştirme → beta onboarding.** 3.7'nin ortasında bu ameliyata girmek deploy'a risk eklerdi, ama gerçek işletmeleri `localStorage`'daki bir JWT ile beta'ya almak da kabul edilemez — bu yüzden "ileride" gibi belirsiz değil, deploy'dan hemen sonraki somut adım olarak ROADMAP 3.11'e yazıldı. |
| Caddy container root çalışıyor | **(2026-09-01) Kabul edildi, kısmen sertleştirildi.** Resmi Caddy image'ı varsayılan olarak root — 80/443 gibi ayrıcalıklı portlara bind etmek bunu gerektiriyor. Non-root'a tam geçiş (host portu 8080'e çevirip Caddy'yi ayrıcalıksız portta dinletmek) otomatik HTTPS/ACME akışını bozup bozmadığı gerçek bir domain'le doğrulanmadan denenmeyecek (Faz 3.8). Bunun yerine bedava bir ara sertleştirme uygulandı: `cap_drop: [ALL]` + `cap_add: [NET_BIND_SERVICE]` + `security_opt: no-new-privileges:true` — root kalıyor ama sahip olduğu yetki SADECE ayrıcalıklı porta bind etmekle sınırlanıyor, diğer tüm Linux capability'leri (CAP_SETUID, CAP_SYS_ADMIN vb.) düşürülüyor. Canlı doğrulandı: bu kısıtlamayla Caddy hâlâ 80/443'e bind edip normal çalışıyor. |
| Migration'lar geriye uyumlu yazılmalı (expand-contract) | **(2026-09-01, Faz 3.8 planlaması — beta öncesi kesin kural.)** Rollback mekanizması (bkz. RUNBOOK.md) SADECE kod/image seviyesinde geri dönüş sağlıyor — Flyway migration'ları geriye ALINMIYOR (bu projede hiç yapılmadı, forward-only). Bu şu anlama geliyor: bir deploy şema değişikliği içeriyorsa ve o deploy'da BAŞKA bir şey bozulursa, image'ı eski SHA'ya döndürmek YETMEZ — eski kod, YENİ şemayla karşı karşıya kalır (olmayan bir kolonu okumaya çalışır, ya da yeni NOT NULL bir kolonu hiç doldurmaz). Canlı test edilip kanıtlandı: Postgres DDL'i transactional olduğu için başarısız bir migration şemayı YARIM bırakmıyor (`Changes successfully rolled back`), ama bu sadece "migration'ın kendisi" için geçerli — bir ÖNCEKİ migration başarıyla uygulanıp deploy edildikten SONRA kod'da rollback yapılırsa, o migration'ın sonucu (yeni kolon/tablo) geride kalır, eski kod bundan habersizdir. **Kural: her migration, bir önceki kod sürümüyle de çalışacak şekilde yazılır (expand-contract).** Pratikte: yeni bir kolon eklerken NOT NULL zorunlu kılınmaz (varsayılan değerle veya nullable eklenir); bir kolonu/tabloyu KALDIRMAK isteyen bir migration, önce onu kullanmayı bırakan bir kod deploy'undan SONRAKİ bir migration'da yapılır, aynı deploy'da değil. Bu, beta'da gerçek müşteri verisi işlenmeye başlamadan ÖNCE ekibin (yani gelecekteki AI oturumlarının da) içselleştirmesi gereken bir disiplin — "rollback var" güvencesi bu kural uygulanmadan yanlış bir güvencedir. |
| `DatabaseSeeder` prod'da asla çalışmaz | **(2026-09-01, Faz 3.8 planlaması)** `@Profile("dev")` class-level anotasyonu ile sınırlı (bkz. `DatabaseSeeder.java` başındaki gerekçe yorumu) — Spring bu profil aktif değilse bean'i hiç OLUŞTURMUYOR, `CommandLineRunner.run()` çağrılmıyor bile. Canlı doğrulandı: `SPRING_PROFILES_ACTIVE=prod` ile tamamen taze bir stack açılıp doğrudan `psql` ile `users`/`businesses` tabloları sorgulandı — ikisi de 0 satır, loglarda seeder'a ait tek bir iz yok. Bu, API yanıtına değil doğrudan veritabanına bakan bir kanıt olduğu için "seeder çalışmadı ama başka bir yoldan veri girdi" ihtimalini de kapatıyor. |
| Ham exception mesajı loglama | **(2026-08-31)** Bir `catch` bloğunda `ex.getMessage()` / `ex.getMostSpecificCause().getMessage()` gibi ÜÇÜNCÜ TARAF (DB sürücüsü, Jackson, vb.) ürettiği bir mesaj loglanacaksa, o mesajın PII (ör. e-posta) taşıyabileceği **her seferinde** değerlendirilmeli — bkz. `GlobalExceptionHandler`'daki iki call site (`DataIntegrityViolationException`, `HttpMessageNotReadableException`), ikisi de `PiiMasker.maskEmails()`'ten geçiriliyor çünkü `users.email` UNIQUE kısıtı ihlali veya Jackson'ın ayrıştıramadığı ham istek gövdesi, e-postayı düz metin olarak mesaja gömebiliyor. Genel bir "her logu regex'le tara" filtresi bilerek yok — sadece bilinen call site'lara elle uygulanıyor. Yeni bir yerde ham exception mesajı loglanacaksa `PiiMasker`'ın oraya da uygulanması gerekip gerekmediği değerlendirilmeli. **Önemli tuzak (canlı bulundu):** bizim kendi `catch` bloğumuzu maskelemek yetmeyebilir — kullandığımız framework'ün KENDİ dahili logger'ı, bizim hiç çağırmadığımız bir noktada aynı ham mesajı BAĞIMSIZ olarak zaten loglamış olabilir. Somut örnek: Hibernate'in `org.hibernate.orm.jdbc.error` logger'ı, `GlobalExceptionHandler` devreye girmeden ÖNCE, aynı Postgres exception'ının ham `Detail: Key (email)=(...)` satırını kendi başına basıyordu — `PiiMasker` oraya hiç ulaşamıyordu çünkü o log çağrısı bizim kodumuzda değil. Çözüm `application-prod.properties`'te `logging.level.org.hibernate.orm.jdbc.error=OFF` (sadece prod, dev/test'te gerçek PII yok). Yeni bir üçüncü taraf kütüphane eklenince aynı soru sorulmalı: bu kütüphanenin kendi dahili logger'ı, bizim hiç görmediğimiz ham veriyi loglar mı? |
| Hesap silme eşikleri (Faz 3.9) | **(2026-09-02)** Üç ayrı, birbirine KARIŞTIRILMAMASI gereken süre: **gracePeriod** (30 gün, varsayılan) — kimlik anonimleştirmesine kadar, USER ve BUSINESS_OWNER PAYLAŞIYOR, bu süre boyunca hesapta GERÇEKTEN hiçbir şey değişmez (geri dönüş/ele geçirme kurtarma senaryosu için). **businessNoticePeriod** (72 saat) — SADECE BUSINESS_OWNER, talep ANINDA bu süre içindeki randevular hemen iptal + bildirim. **businessReversalWindow** (48 saat) — SADECE BUSINESS_OWNER, talep geri alınmazsa kalan TÜM randevular topluca iptal. `reversalWindow <= noticePeriod` matematiksel zorunluluk (`AccountDeletionProperties`'in `@PostConstruct`'ı doğruluyor) — aksi halde iki eşik arasına düşen bir randevu hiç yakalanamaz. Üçü de `application.properties`'te ayrı ayrı config, koda gömülü değil. |
| Askıdaki işletme sahibi panelde ne yapabilir | **(2026-09-02)** Salt okunur + sadece "talebi iptal et" aktif. Okuma uçları (`OwnershipGuard.assertOwnsBusiness`/`assertOwnsServiceItem`/`assertOwnsStaff`) askıda olsa da geçer — sahip randevularını/inbox'ını/personelini görmeye devam eder. Mutasyon uçları (`assertOwnsActiveBusiness`/`assertOwnsActiveServiceItem`/`assertOwnsActiveStaff` + `AppointmentService.changeStatus`'ta APPROVE/REJECT/NO_SHOW engeli) askıdaysa 409. CANCEL bilerek istisna — hem müşteri kendi randevusunu her zaman iptal edebilmeli hem de silme akışının kendi otomatik iptalleri aynı yolu kullanıyor. |
| Silme talebi anında JWT'ler geçersiz kılınmıyor | **(2026-09-02) Bilinçli kabul.** `deletionRequestedAt` dolduğunda (henüz `anonymizedAt` değil) mevcut token'lar çalışmaya devam eder — bu bir açık değil: talep zaten şifre istiyor (ele geçirilmiş şifresiz oturum senaryosu yok), gerçek sahip `enabled=true` olduğu sürece şifresiyle yeniden login olup iptal ucuna ulaşabilir (kurtarma buna bağımlı değil). Bu projede JWT tamamen stateless — şifre değişikliğinde bile "bu andan önceki token'lar geçersiz" mekanizması yok, SADECE silme talebi için eklemek tutarsız olurdu. Genel bir oturum sonlandırma ihtiyacı doğarsa (ör. "şüpheli giriş"), hem şifre değişikliğini hem silme talebini kapsayan ayrı bir görev olmalı. **AYRI ve GERÇEK bir bug olarak canlı bulunup düzeltildi:** anonimleştirme SONRASI (`anonymizedAt` dolu) eski bir token'la istek atılırsa, `JwtFilter`'ın `loadUserByUsername` çağrısı token'daki artık-var-olmayan eski email'i bulamayıp `UsernameNotFoundException` fırlatıyordu — bu, `ExceptionTranslationFilter`'a hiç ulaşmadan (zincirde ondan önce çalıştığı için) kontrolsüz bir 500'e düşüyordu. `JwtFilter` artık bunu yakalayıp kimliksiz devam ediyor, temiz 401 dönüyor (bkz. `JwtFilterAnonymizedUserTest`). |

---

## Çalışma Kuralları — Bunlara harfiyen uy

### 1. Adım adım ilerle
Tek seferde birden fazla özellik bitirme. Her adımda tek bir konuya odaklan, geliştirici
onaylayınca sonrakine geç. Adımlar ROADMAP.md'de numaralı ve tek oturumluk boyutta.

### 2. Kim yazacak — konsept bazlı ayrım

**Geliştirici yazar, AI yapıyı ve nedenini anlatır** — bu projede ilk kez karşılaşılan
konsept/pattern ise. Burada struggle etmek işin özü:
- JWT auth, Security config
- Interval overlap / slot hesaplama algoritması
- Rol bazlı yetkilendirme, sahiplik kontrolleri
- Global exception handling
- SOLID refactor'ler
- Flyway, `Staff` modeli, `Review` garantisi, durum makinesi

**AI yazar** — bir kez anlaşılıp uygulanmış, tekrar eden şey ise:
- Yeni entity için CRUD controller, yeni DTO, standart mapper
- Seeder güncellemeleri, Tailwind ekranları, konfigürasyon dosyaları

AI yazdığında bile üstünkörü "tamam" denmez; her satır okunur.
**Anlaşılmayan bir satır varsa durulur ve sorulur.**

### 3. Her metodun üstünde kısa bir yorum satırı olsun
O metodun ne yaptığını bir cümleyle anlatan. Uzun javadoc değil, kısa ve net.

### 4. Neden'i her zaman açıkla
Bir pattern, anotasyon veya yaklaşım önerirken **"bunu neden böyle yapıyoruz, alternatifi neydi,
neden onu seçmedik"** kısmını da yaz.

### 5. SOLID prensiplerine tam uyum
Bir yerde SOLID ihlali görürsen (mevcut kodda dahil) söyle ve nasıl düzelteceğini anlat.

### 6. Güvenlik pazarlık konusu değil
- Bir işletme sahibi başkasının verisine **asla** erişememeli
- Input validation her zaman
- Entity'ler response olarak dönülmez — DTO kullanılır (PII sızıntısı)
- Hata mesajları iç detay ve stack trace sızdırmaz
- Kullanıcı kimliği path parametresinden değil, **daima token'dan** alınır

### 7. Belirsizse sor
Tahmin edip ilerleme.

### 8. Türkçe konuş
Samimi ve doğrudan bir dil. Gereksiz övgü ve dolgu cümle yok.

### 9. İddia etme, doğrula
"Kod okudum, çalışıyor görünüyor" bir doğrulama değil. Bir davranış iddia edilecekse
gösterilir: curl ile uç nokta, `psql` ile veri, tarayıcıda gerçek akış. Güvenlik
düzeltmelerinde açık önce sömürülür, sonra kapatılır, sonra aynı saldırının geçmediği
gösterilir. Varsayımın doğru çıkması, doğrulamayı gereksiz kılmaz — bu oturumda "EXPIRED
randevuya yorum yapılamıyor, otomatik" varsayımı doğruydu ama test edilene kadar sadece
varsayımdı.

Test verisi **her zaman temizlenir**; veritabanında iz bırakılmaz.

### 10. Kendi hatanı söyle
Yanlış bir şey iddia ettiysen düzelt ve neden yanıldığını söyle. Bu oturumda birkaç kez
oldu: "create ve update ikisi de açık" (update zaten güvenliydi), "satır polling yüzünden
kayboldu" (aslında test regex'i yanlış butona tıklamıştı). Yanlış teşhisi sessizce
düzeltmek, sonraki oturumu aynı yanlışa götürür.

---

## Mevcut Durum

**Faz 0, 1, 2 tamamlandı. Faz 3 sürüyor: 3.1-3.7 tamamlandı, 3.8 (deploy) henüz
BAŞLAMADI, 3.9 (KVKK) kısmen tamamlandı.** Güncel faz ilerleme tablosu için
ROADMAP.md'nin sonundaki checklist tek otorite — burası sadece kısa bir özet, ayrıntı
için oraya bak.

**Migration seviyesi: V15.** Prod **henüz deploy edilmedi** — Dockerfile/compose ve
`RUNBOOK.md` (Faz 3.7/3.8'de) hazırlandı, ama gerçek sunucuya ilk deploy (3.8a) daha
yapılmadı. Tek veritabanı hâlâ yerel geliştirme ortamı.

**Faz 3.9 (hesap silme akışı, KVKK unutulma hakkı) — backend tamamlandı ve test
edildi, frontend ve hukuki metinler (aydınlatma/VERBİS/sözleşme) yapılmadı.** Detay:
ROADMAP.md 3.9, karar tablosundaki "Hesap silme eşikleri" ve "Askıdaki işletme sahibi
panelde ne yapabilir" satırları.

### Bu aşamada eklenenler (git geçmişinde detaylı gerekçeleriyle)

- Favoriler, profil paneli (bilgi/şifre/özet), arayüz yeniden tasarımı (açık tema + lacivert)
- `ServedGender` modeli (BARBER kaldırıldı), onaylı işletme rozeti
- **Tek saat kaynağı** (`Clock` bean + TZ sabitleme) — ayrı commit, `TimeConfigTest` ile korunuyor
- **Talep zaman aşımı**: `AppointmentExpiryPolicy` (saf sınıf, `Clock` almaz — `now` parametre),
  `EXPIRED` durumu, `AppointmentLifecycleScheduler`, randevu ufku, açık talep sınırı
- Güvenlik: hizmet uçlarında kiracılar arası ele geçirme açığı (canlı sömürüldü ve kapatıldı),
  personel uçlarına sahiplik, login hata yönetimi handler'a taşındı
- Test altyapısı (Testcontainers Postgres), yetkilendirme entegrasyon testleri, API dokümantasyonu
- Bildirim altyapısı (`NotificationPort`, kanal-bağımsız), rate limiting, loglama/izleme
- İşletme kapak fotoğrafı yükleme (`BusinessPhotoService`, yerel disk depolama)
- Konteynerleştirme (Dockerfile'lar, docker-compose, Caddy) — henüz deploy edilmedi
- Hesap silme akışı (USER + BUSINESS_OWNER, backend) — bkz. yukarısı

### Bilinen açık işler

| Konu | Durum |
|---|---|
| `MyAppointmentsPage`: `EXPIRED` rozeti + `expiresAt` gösterimi | **Yapılmadı** — backend hazır, `expiresAt` API'den geliyor |
| `InboxTab`'de son tarih gösterimi | **Yapılmadı** (canlı yenileme yapıldı) |
| İşletme başına "otomatik onay" anahtarı | Karar verildi, yazılmadı (~45 dk) |
| `/me/upcoming` ile profil "yaklaşan" sayısı tutarsız | `CANCELLED`/`NO_SHOW` sayıyor; ayrı görev olarak açıldı |
| `favorites.created_at`'te `DEFAULT now()` | Kullanılmıyor ama şemada duruyor; ayrı küçük migration ile temizlenecek |
| İl/ilçe ile manuel konum seçimi | Tasarım konuşuldu (`city`/`district` alanları), yazılmadı |
| Hesap silme akışı — frontend | **Yapılmadı** — silme UI'ı (şifre onaylı), askıdaki hesap banner'ı, geçmiş randevuda askıdaki işletme adının link değil düz metin olması |
| Faz 3.8 deploy (sunucu, DNS, ilk canlı deploy, yedekleme) | **Başlamadı** — plan/RUNBOOK.md hazır |

### Doğrulama beklentisi

Bu projede **"kod okudum, doğru görünüyor" yeterli sayılmıyor.** Bir davranış iddia
edilecekse canlı doğrulanır: curl ile uç noktalar, `psql` ile veri, tarayıcıda gerçek akış.
Güvenlik düzeltmelerinde açık önce **sömürülüp** sonra kapatıldığı gösterilir. Test verisi
her zaman temizlenir.

Yol haritası ve faz planı: [ROADMAP.md](ROADMAP.md)
