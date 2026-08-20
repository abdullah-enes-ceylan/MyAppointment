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

**Frontend:** React 19 + Vite + Tailwind CSS 4. Koyu tema üzerine zümrüt yeşili ve kehribar/turuncu
vurgular. Axios ile API haberleşmesi, protected route yapısı, JWT'deki role göre reaktif
navigasyon menüsü.

### Komutlar

```bash
cd backend && ./mvnw spring-boot:run
```

```bash
cd frontend && npm run dev
```

---

## Verilmiş Mimari Kararlar

Bu kararlar tartışılıp verildi; yeniden açmadan önce sor.

| Konu | Karar |
|---|---|
| Tenant modeli | Bir sahip **N işletme** yönetebilir. `businessId` **asla JWT'ye gömülmez**; her istekte sahiplik DB'den doğrulanır. |
| Personel/kapasite | Faz 2'de tam `Staff` modeli. "Aynı saate 2 kişi" ihtiyacının çözümü budur, basit kapasite alanı değil. |
| Bildirim kanalı | **Karar ertelendi.** Faz 3'te kanal-bağımsız `NotificationPort` soyutlaması kurulur; kanal seçilince tek adapter eklenir. |
| Para tipi | `BigDecimal(10,2)` — `double` değil. |
| Zaman | `LocalDateTime` + `Europe/Istanbul`. Sunucu TZ'si UTC'ye sabitlenir. |
| Konum sorgusu | Bounding box ön filtresi + Haversine. PostGIS değil (ucuz hosting'de bakım yükü). |
| Puan ortalaması | Önce aggregate sorgu; denormalizasyon ancak ölçek gerektirince. |

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

---

## Mevcut Durum ve Bilinen Kritik Sorunlar

Kod tabanı denetlendi. Uçtan uca akış çalışıyor (kayıt → login → kategori → hizmet → slot →
randevu → inbox → onay/ret) ancak **authorization katmanı neredeyse yok**.

Faz 0 tamamlanana kadar aşağıdakiler açık sayılır — üstüne yeni özellik yazma:

1. `POST /api/users/register` ham entity alıyor → `role: "ADMIN"` veya `id` göndererek
   yetkisiz admin olunabiliyor / mevcut hesap ezilebiliyor
2. `businessId` bazlı tüm uçlarda sahiplik kontrolü yok → kiracılar arası veri sızıntısı
3. `ServiceItemController` create/update/delete tamamen korumasız
4. Servis/işletme eşleşmesi doğrulanmıyor
5. `durationInMinutes == 0` → slot hesabında sonsuz döngü (kimlik doğrulamasız DoS)
6. `GET /api/businesses` (permitAll) işletme sahibinin email/telefonunu sızdırıyor
7. Sırlar (`jwt.secret`, DB parolası) git'te takipli
8. Randevu oluşturmada yarış koşulu — `@Transactional` yok, DB constraint yok
9. Hiç input validation, hiç exception handler yok
10. `ddl-auto=create-drop` — her restart'ta veritabanı siliniyor

Detaylı analiz ve çözüm sırası: [ROADMAP.md](ROADMAP.md)
