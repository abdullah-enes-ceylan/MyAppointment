# Deploy Runbook — Faz 3.8

Bu dosya çalıştırılabilir bir prosedürdür — sırayla, sunucuda, tek tek uygulanır. Her adımın
altında **kanıt satırı** var: o komutu çalıştırdıktan sonra ekranda TAM OLARAK ne görmen
gerektiğini söylüyor. Beklenenden farklı bir şey görürsen bir sonraki adıma geçme, orada dur.

## Neden bu kadar titiz — kanıt disiplini

Bu projede geliştirme Windows + Docker Desktop üzerinde yapıldı. İki kez, birbirinden bağımsız
konuda, **yerel test yanıltıcı çıktı**:

- Postgres'in 5432 portunun host'a açık göründüğü test — aslında `docker-compose.yml` hiç port
  açmıyordu, bu geliştirme makinesinde zaten ayrı, yerel bir Postgres servisi 5432'yi
  dinliyordu. `docker compose ps` ile teşhis edildi.
- `business-photo-storage` bind mount'unun izin sorunu — Windows'ta host dosya izinleri
  container'a gerçek anlamda yansımadığı için hiç görünmedi, gerçek bir Linux sunucuda
  `Permission denied` ile patlardı. Named volume'a geçilerek kapatıldı.

İkisi de aynı desen: **temiz görünen bir test, kirli (Windows) bir ortam yüzünden gerçeği
gizliyordu.** Bu yüzden bu runbook'ta hiçbir madde "muhtemelen çalışır" demiyor — her madde ya
`[SUNUCUDA DOĞRULANACAK]` diye açıkça işaretli (yerelde kanıtlanamayan), ya da yerelde gerçekten
ölçülmüş/test edilmiş somut bir kanıtla destekleniyor. Adım tamamlandığında kutucuğu işaretle,
kanıtı görmeden işaretleme.

## Faz bölünmesi

- **3.8a** (bu dosyanın "Bölüm A"sı) — sunucu kurulumu, sertleştirme, DNS, ilk deploy, doğrulama.
- **3.8b** (bu dosyanın "Bölüm B"si) — yedekleme, restore provası, izleme. **3.8a tamamen
  bitmeden ve doğrulama turu geçmeden başlanmaz.**

## Ön koşul — 3.8a başlamadan ÖNCE kapatılmış iki soru

Bunlar runbook'a değil, buraya yazılıyor çünkü zaten canlı kanıtla kapatıldı — sunucuda tekrar
denemene gerek yok, ama NEDEN güvenle ilerleyebildiğimizi bilmen için:

1. **`DatabaseSeeder` prod'da hiç çalışmaz.** `@Profile("dev")` ile sınırlı — tamamen taze bir
   `SPRING_PROFILES_ACTIVE=prod` stack'inde `psql` ile doğrudan `users`/`businesses` tabloları
   sorgulandı, ikisi de 0 satır. Beta'ya davet edeceğin işletme uygulamayı ilk açtığında sahte
   Istanbul işletmeleri GÖRMEYECEK.
2. **Flyway migration ortada patlarsa şema yarım kalmıyor, ama sessiz kalırsa fark edilmez.**
   Bilerek bozuk bir migration ile test edildi: Postgres DDL transactional olduğu için başarısız
   migration tam olarak geri alınıyor (`Changes successfully rolled back`), şema hiçbir zaman
   yarım/bozuk durumda kalmıyor. Ama uygulama context'i başlatamayıp çöküyor, `restart:
   unless-stopped` onu tekrar tekrar deniyor — **gerçek bir crash-loop** (canlı gözlemlendi).
   Bunun sessizce sürmemesi TAMAMEN A9'daki `/actuator/health` izlemesine bağlı — bu yüzden
   izleme (Bölüm B) atlanabilir bir "sonra yaparım" maddesi değil.
3. **V1→V14 zincirinin TAMAMEN BOŞ bir veritabanında baştan sona çalıştığı ayrıca kanıtlandı.**
   Dev veritabanı zaten migrate edilmiş durumda olduğu için bu yol günlük kullanımda hiç
   sınanmıyor — prod'daki ilk kalkış, bu zincirin sıfırdan uçtan uca ilk gerçek denemesi.
   Postgres volume'u tamamen silinip (`docker volume rm`) prod profiliyle sıfırdan kalkış
   yapıldı, sonra doğrudan `flyway_schema_history` sorgulandı:
   ```
    installed_rank | version | description                 | success
   ----------------+---------+-----------------------------+---------
                  1 | 1       | baseline                    | t
                  2 | 2       | working hours               | t
                  ...
                 14 | 14      | in app notifications        | t
   ```
   14 satırın hepsi `success=t`, `\dt` ile 14 uygulama tablosunun hepsi gerçekten oluşmuş
   görüldü, ve uygulama bu şema üzerinde `/api/businesses` ve `/actuator/health`'e normal
   yanıt verdi. **Bu, zincirin GEÇERLİ olduğunu kanıtlıyor — ama sunucudaki asıl çalıştırma
   yine de ayrıca doğrulanmalı** (farklı donanım, farklı Postgres/Docker sürümü kombinasyonu
   olabilir). Bu yüzden A8'in kanıt adımına aynı `flyway_schema_history` kontrolü ZORUNLU bir
   satır olarak eklendi — beş saniyelik bir sorgu, atlanacak yer değil.

---

# BÖLÜM A — Sunucu kurulumu, sertleştirme, DNS, ilk deploy

## A0. Sunucu seçimi (karar, komut değil)

**Önerilen: Hetzner CX22 (2 vCPU / 4 GB RAM / 40 GB disk, ~4,5 €/ay).**

Bu makinede ölçülen gerçek çalışma-zamanı bellek kullanımı (üç servis de yeni açılmış, boşta):

| Servis | Bellek |
|---|---|
| Caddy | ~10 MB |
| Backend (JVM) | ~347 MB |
| Postgres | ~57 MB |
| **Toplam** | **~414 MB** |

4 GB'lık bir sunucuda çalışma zamanı için bolca yer var. Asıl belirsizlik **build anı**: Maven
(`mvn package`) ve Node/Vite (`npm run build`) aynı `docker compose build` çağrısında sırayla
çalışıyor, ikisi de geçici bellek sıçraması yapabilir.

**Bu, bu makinede ÖLÇÜLMEYE ÇALIŞILDI ve ölçülemedi — tahmin değil, başarısız bir ölçüm
girişimi.** Maven'in gerçekten derlediği (log'da "Compiling 131 source files" görülen) pencerede
`docker stats` ve `docker ps -a` saniyede bir, 40 saniye boyunca kontrol edildi — hiçbir build
container'ı hiçbir zaman listede görünmedi. Bu Docker Desktop kurulumunda BuildKit'in build
süreci host'un container listesine hiç yansımıyor; bu yüzden gerçek tepe bellek değeri
**sadece sunucuda** ölçülebilir — aşağıda A8'de bunun için bir adım var, **atlama, sonucu bu
dosyaya geri yaz.**

Bu proje ölçeğinde (tek Spring Boot uygulaması, tek React SPA — 131 Java kaynak dosyası, 152
frontend modülü, ~558 KB'lık tek bir JS bundle) build'in 1-2 GB'ı aşması beklenmez, ama gerçek
sayı ölçülene kadar bu bir varsayım. **Build sunucuda yapılacağı** (CI/registry altyapısı yok,
kurmak bu ölçekte orantısız karmaşıklık olurdu) için ucuz bir sigorta gerekiyor: **2 GB swap
dosyası zorunlu** (aşağıda A1.7). Bunu atlama.

Sunucuyu bundan küçük almayı düşünüyorsan durup tekrar konuşalım — 4 GB + swap altına inmek bu
planın varsayımlarını geçersiz kılar.

**Ölçülen gerçek build tepe değeri (A8'den sonra buraya yaz):** `___ MB` (henüz ölçülmedi)

## A1. İlk bağlantı ve sertleştirme

Bu bölüm root ile ilk girişten sonra, host çıplak haldeyken yapılır. Container'lar ne kadar sıkı
olursa olsun host ele geçirilirse hepsi düşer — bu yüzden ilk bölüm bu.

### A1.1 — Yeni sudo kullanıcısı oluştur

```bash
adduser deploy
usermod -aG sudo deploy
```

**Kanıt:** `id deploy` → çıktıda `sudo` grubu görünmeli.

### A1.2 — SSH key kopyala (kendi makinenden, deploy kullanıcısına)

Kendi makinende (sunucuda değil):

```bash
ssh-copy-id deploy@SUNUCU_IP
```

**Kanıt:** `ssh deploy@SUNUCU_IP` parola sormadan bağlanmalı.

### A1.3 — SSH: parola auth ve root login'i kapat

Sunucuda, `deploy` kullanıcısıyla (root ile değil — kendini kilitleme riski):

```bash
sudo sed -i 's/^#*PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo sed -i 's/^#*PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
sudo systemctl restart sshd
```

**Kanıt:** Yeni bir terminalden (**eskisini kapatmadan**) `ssh deploy@SUNUCU_IP` ile bağlanmayı
dene — hâlâ bağlanabiliyor olmalı. Bağlanamıyorsan eski terminal hâlâ açık, oradan geri al.
Ardından `ssh root@SUNUCU_IP` dene — **reddedilmeli**.

### A1.4 — `ufw` — sadece 22/80/443

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

**Kanıt:** `sudo ufw status` → `22`, `80`, `443` `ALLOW`, başka hiçbir şey listede olmamalı.

### A1.5 — `fail2ban`

```bash
sudo apt update && sudo apt install -y fail2ban
sudo systemctl enable --now fail2ban
```

**Kanıt:** `sudo systemctl status fail2ban` → `active (running)`.

### A1.6 — `unattended-upgrades`

```bash
sudo apt install -y unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades
```

Sorulan diyalogda "Evet" seç.

**Kanıt:** `cat /etc/apt/apt.conf.d/20auto-upgrades` → `APT::Periodic::Unattended-Upgrade "1";`
satırı olmalı.

### A1.7 — 2 GB swap (A0'daki build-bellek sigortası)

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

**Kanıt:** `free -h` → `Swap:` satırında `2.0Gi` görünmeli.

## A2. Docker kurulumu

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker deploy
sudo systemctl enable docker
```

`systemctl enable docker` OLMADAN, A6'daki `restart: unless-stopped` hiçbir işe yaramaz — reboot
sonrası Docker DAEMON'unun kendisi kalkmazsa, container'ların restart politikası devreye hiç
girmez. Çoğu dağıtımda Docker paketleri bunu zaten varsayılan yapar ama elle doğrula.

**`deploy` kullanıcısı `docker` grubunda — bunun pratik anlamını bil:** `docker` grubu üyeliği,
pratikte root'a EŞDEĞERDİR (bir container'ı host'un kök dizinini mount ederek çalıştırıp oradan
host dosya sistemine tam erişim sağlamak mümkün). Bu, `deploy` kullanıcısını sudo'suz "güvenli"
bir kullanıcı gibi görmemen gerektiği anlamına geliyor — A1'deki SSH sertleştirmesi (key-only,
root login kapalı) bu yüzden gerçek bir güvenlik sınırı, "docker grubu = sudo değil" yanılgısı
değil.

Bu adımdan sonra **çıkış yapıp tekrar SSH ile bağlan** (grup üyeliğinin geçmesi için).

**Kanıt:** `docker run hello-world` → "Hello from Docker!" mesajı, `sudo` olmadan çalışmalı.
`systemctl is-enabled docker` → `enabled` dönmeli.

## A3. DNS — Caddy'nin ACME denemesinden ÖNCE

**⚠️ Domain Cloudflare'den alındıysa: kayıtları eklerken proxy KAPALI olacak (gri bulut,
"DNS only" — turuncu "Proxied" DEĞİL).** Proxy açıkken Cloudflare kendi IP'sini döndürür ve
gerçek trafiği kendi üzerinden geçirir — Let's Encrypt'in HTTP-01 doğrulaması (A8/A10)
sunucuna DOĞRUDAN ulaşamadığı için **başarısız olur**, Caddy hiçbir zaman geçerli bir
sertifika alamaz. Cloudflare DNS panelinde her kayıt satırının yanındaki bulut ikonu gri
olmalı, turuncu değil.

Alan adı sağlayıcında bir **A kaydı** ekle: `randevumweb.com` → sunucunun IP'si. Ayrıca
**`www` için ayrı bir A kaydı** (aynı IP, ya da CNAME → `randevumweb.com`) — Caddyfile'da
`www.` yönlendirmesi bu kayıt OLMADAN hiç eşleşmez, kimse `www` yazınca site açılmaz.

**Kanıt — HARİCİ bir resolver'a sor, yerel/ISS resolver'ına DEĞİL.** Yerel resolver'ın kendi
önbelleği "hazır" gibi görünüp asıl yayılma tamamlanmadan seni yanıltabilir. Kendi makinenden:
```bash
dig @8.8.8.8 +short randevumweb.com
dig @8.8.8.8 +short www.randevumweb.com
```
Ayrıca **sunucunun kendisinden**, dış dünyanın onu nasıl gördüğünü kontrol et (sunucu kendi
DNS'ini farklı görüyor olabilir):
```bash
dig @8.8.8.8 +short randevumweb.com
curl -s ifconfig.me
```
**Beklenen: ikisi de sunucunun gerçek IP'si, BİREBİR aynı.** İki farklı yanlış çıktı türü var,
ayrı ayrı ele alınmalı:
- Sunucunun IP'sinden TAMAMEN farklı, ama **Cloudflare'in kendi IP aralığına ait** (`104.16.*`,
  `104.17.*`, `172.64-71.*`, `162.158.*` gibi) bir sonuç dönüyorsa — bu "DNS henüz yayılmadı"
  DEĞİL, **proxy hâlâ açık (turuncu bulut)** demektir. Cloudflare panelinden kaydı "DNS only"a
  çevir, birkaç dakika bekleyip tekrar dene.
- Sunucunun IP'sinden farklı ama Cloudflare aralığına da benzemeyen (ör. eski bir hosting IP'si)
  bir sonuç dönüyorsa, bu gerçek DNS yayılma gecikmesi — **bekle**, dakikalar ile saatler
  arasında sürebilir, yayılmadan A8'deki (ACME) adıma geçme. Prod CA'nın saatte 5 hata limiti
  var, yayılmayı beklemeden denemek bu limiti gereksiz yere tüketir.

## A3.1. Caddy syntax/smoke testi — DNS yayılmadan asla prod CA'ya istek gitmez

**Kural, DNS henüz yayılmamışken (ya da hiç yayılmasını beklemeden, sadece syntax doğrulamak
için) geçerli — sebep ne olursa olsun:** `frontend/Caddyfile`'ı hiçbir şekilde gerçek (production)
Let's Encrypt CA'sına karşı test etme. Bunu bir kez, sırf syntax kontrolü için yaptık — DNS henüz
`randevumweb.com`'a işaret etmiyorken container'ı `caddy run` ile gerçekten ayağa kaldırdık,
Caddy production CA'ya karşı gerçek bir hesap açtı ve gerçek bir authorization denemesi yaptı
(başarısız oldu, çünkü DNS yok — ama deneme yine de SAYILDI). O anki DNS durumu masumdu (domain
gerçek trafiğe hiç açılmamıştı), ama **deploy gününde aynı hatayı yapmak masum olmaz**: A8'deki
ilk gerçek deploy tam da "Caddyfile/DNS ayarı ilk seferde tutmayabilir, birkaç kez düzeltip
tekrar deneyeceksin" varsayımıyla kurulu — her "düzelttim, bi' bakayım" denemesi prod CA'ya
gidiyorsa, tam da o güne, tam da o ana ayrılmış olan saatte-5-hata bütçesini gerçek deploy'dan
ÖNCE tüketebilirsin. Bütçe bittiğinde bir saat beklemekten başka çare yok — deploy günü bu
kabul edilebilir bir kayıp değil.

**İki güvenli yöntem — hangisini test ettiğine göre seç:**

**(a) Salt syntax kontrolü (ağa hiç çıkmaz) — `auto_https off` ile TLS tamamen kapalı.**
Herhangi bir CA'ya (staging dahil) hiç istek gitmesin istiyorsan, bunu kullan (bizzat
çalıştırılıp doğrulandı):
```bash
cd frontend
{ printf '{\n\tauto_https off\n}\n'; cat Caddyfile; } > ./Caddyfile.smoketest
# Dosya frontend/ ICINDE olmali, /tmp DEGIL: bu makinede (Windows + Docker
# Desktop) /tmp'yi mount etmeye calismak "mount src=/tmp/...: not a
# directory" hatasiyla patladi -- Docker Desktop'in dosya paylasimi proje
# dizinini icerir, /tmp'yi degil. Ayrica MSYS_NO_PATHCONV olmadan Git Bash
# container-ici /etc/caddy/... yolunu host yoluna cevirip "no such file"
# hatasi verir (bu oturumda ikisi de yasandi).
MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$(pwd)/Caddyfile.smoketest:/etc/caddy/Caddyfile" \
  -e DOMAIN=randevumweb.com -e WWW_DOMAIN=www.randevumweb.com \
  caddy:2-alpine \
  caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
rm -f ./Caddyfile.smoketest
```
**Beklenen:** `Valid configuration`, log satırında `automatic HTTPS is completely disabled`.
Sıfır ağ isteği — parse/adapte etme dışında hiçbir şey yapmaz, bu yüzden DNS durumundan
tamamen bağımsız, HER ZAMAN güvenli. (`Caddyfile.smoketest` `.gitignore`'da — yine de `rm`'i
atlama.)

**(b) Gerçek ACME akışını da görmek istiyorsan — staging CA'ya sabitle, ASLA prod'a değil.**
A8'deki geçici bloğun aynısını, ayrı bir dosyada dene (bizzat çalıştırılıp doğrulandı):
```bash
cd frontend
{ printf '{\n\tacme_ca https://acme-staging-v02.api.letsencrypt.org/directory\n}\n'; cat Caddyfile; } \
  > ./Caddyfile.smoketest
MSYS_NO_PATHCONV=1 docker run --rm -d --name caddy-smoke-test \
  -v "$(pwd)/Caddyfile.smoketest:/etc/caddy/Caddyfile" \
  -e DOMAIN=randevumweb.com -e WWW_DOMAIN=www.randevumweb.com \
  caddy:2-alpine \
  caddy run --config /etc/caddy/Caddyfile --adapter caddyfile
sleep 3 && docker logs caddy-smoke-test
docker stop caddy-smoke-test
rm -f ./Caddyfile.smoketest
```
**Beklenen:** loglarda **sadece** `"ca":"https://acme-staging-v02.api.letsencrypt.org/directory"`
— `acme-v02.api.letsencrypt.org` (`-staging` OLMADAN) görürsen DUR, yanlış dosyayı
çalıştırıyorsun, container'ı hemen durdur. DNS henüz yoksa yine `no valid A records found`
hatası görürsün, ama bu STAGING'e karşı olduğu için ZARARSIZ — saatte-5 sınırı olan gerçek
bütçeyi hiç etkilemez.

## A4. Kod sunucuya

```bash
cd /opt
sudo mkdir randevum && sudo chown deploy:deploy randevum
git clone <REPO_URL> randevum
cd randevum
```

**Kanıt:** `ls` → `backend/`, `frontend/`, `docker-compose.yml`, `.env.example` görünmeli.

## A5. Prod secret'ları — sunucuda YENİDEN üret, dev'den TAŞIMA

Dev'deki `application-dev.properties`'teki JWT secret ve DB şifresi **asla** buraya kopyalanmaz
— sunucu kendi, taze, rastgele değerlerini üretir.

```bash
cp .env.example .env
JWT_SECRET=$(openssl rand -base64 32)
POSTGRES_PASSWORD=$(openssl rand -base64 24)
sed -i "s|BURAYA_OPENSSL_ILE_URETTIGIN_SECRET|$JWT_SECRET|" .env
sed -i "s|BURAYA_GUCLU_BIR_PAROLA_YAZ|$POSTGRES_PASSWORD|" .env
sed -i "s|BURAYA_GERCEK_DOMAIN_YAZ|randevumweb.com|g" .env
chmod 600 .env
```

`CORS_ALLOWED_ORIGINS` değerini gerçek domain'inle (`https://randevumweb.com`) elle kontrol et —
yukarıdaki `sed` `DOMAIN`, `WWW_DOMAIN` ve `CORS_ALLOWED_ORIGINS`'i AYNI ANDA değiştiriyor (hepsi
`.env.example`'da aynı `BURAYA_GERCEK_DOMAIN_YAZ` yer tutucusunu paylaşıyor), üçünün de doğru
göründüğünden emin ol.

**Kanıt:**
```bash
cat .env
```
`WWW_DOMAIN=www.randevumweb.com` olmalı (`www.BURAYA_GERCEK_DOMAIN_YAZ` DEĞİL — sed'in kaçırdığı
bir satır varsa Caddy o siteyi hiç açamaz, A9'da fark edilir).
→ `JWT_SECRET` ve `POSTGRES_PASSWORD` rastgele karakter dizileri olmalı, **dev'deki
`xmcU1nTSzObx7z9SDUvOZxEGVSyE...` değeri OLMAMALI**. `ls -la .env` → izin `-rw-------` (600),
**sahip `deploy` kullanıcısı olmalı** (root değil — `git clone`'u `deploy` kullanıcısıyla
yaptıysan bu zaten doğru çıkar, ama `sudo` ile herhangi bir adım atladıysan sahiplik root'a
kayabilir, kontrol et).

## A6. `restart: unless-stopped` — UYGULANDI, sunucuda sadece doğrula

**(2026-09-03) Zaten yapıldı** — daha önce `backend`/`caddy`'de `restart: on-failure`,
`postgres`'te HİÇ restart politikası yoktu (ve `on-failure` bir VPS reboot'undan sonra
container'ları otomatik başlatmaz, sadece çöken bir container'ı yeniden dener). Üçü de
`docker-compose.yml`'de `unless-stopped`'a çevrildi — bu, sunucu satın alınmadan/DNS
kurulmadan önce yapılabilecek, koddan bağımsız bir adım olduğu için deploy'u beklemeden
uygulandı.

**Kanıt — sunucuda tekrar doğrula** (kod sunucuya çekildikten sonra, deploy'dan önce):
```bash
grep -n "restart:" docker-compose.yml
```
→ üç satır da `unless-stopped` olmalı. A9'daki reboot testi bunu fiilen kanıtlıyor.

## A7. Rollback için image adlandırması — UYGULANDI, sunucuda sadece doğrula

**(2026-09-03) Zaten yapıldı** — `docker-compose.yml`'deki `backend` ve `caddy` servislerine
sabit `image:` adı eklendi (`randevum-backend`, `randevum-caddy`). Bu isimler runbook'un son
bölümündeki `docker tag randevum-backend:latest randevum-backend:$PREV_SHA` komutlarıyla
BİREBİR eşleşiyor — sunucuda dizin adı `/opt/randevum`'dan farklı çıksa bile (Docker'ın
otomatik ürettiği isim dizin adına bağlı olurdu) rollback komutları hep aynı, sabit isme
karşı çalışır. Koddan bağımsız bir adım olduğu için deploy'u beklemeden uygulandı.

**Kanıt — sunucuda tekrar doğrula:**
```bash
grep -n "^    image: randevum-" docker-compose.yml
```
→ iki satır dönmeli: `randevum-backend` ve `randevum-caddy`.

## A8. İlk deploy — Let's Encrypt STAGING CA ile

**Neden staging, prod değil — deploy günü tipik olarak TEK seferde tutmaz.** Gerçek
(production) Let's Encrypt CA'nın hostname başına **saatte 5 başarısız doğrulama denemesi**
sınırı var. İlk deploy'da bir şeyin ilk seferde tam doğru olmasını BEKLEME — DNS'in son bir
kaydı eksik kalmış olabilir, `.env`'deki `DOMAIN`/`WWW_DOMAIN` yanlış yazılmış olabilir,
`ufw`'de 80 portu unutulmuş olabilir; her biri bir authorization denemesini başarısız
bitirir. Deploy günü doğal akış "dene → başarısız → düzelt → tekrar dene" döngüsü olduğu
için, bu döngüyü PROD CA'ya karşı çalıştırırsan saatte-5 bütçesini tam da gerçek deploy'un
ortasında tüketip bir saat kilitlenmiş olursun. Staging CA'nın kendi (çok daha gevşek) sınırı
var, sertifika tarayıcıda "güvensiz" görünür ama mekanizmanın (ACME akışı, DNS, port 80/443
erişimi) uçtan uca çalıştığını kanıtlamak için yeterli — asıl güvenilir sertifika A10'da,
her şey doğrulandıktan SONRA, tek bir prod CA denemesiyle alınır.

**Bu kural sadece A8'in kendisi için değil — DNS henüz yayılmamışken/Caddyfile'ı sadece
syntax olarak denerken de aynı riski taşır (bkz. A3.1).** O ayrım orada: A8 gerçek deploy
akışının bir parçası, A3.1 ise DNS'ten TAMAMEN bağımsız, herhangi bir zamanda yapılan bir
syntax/smoke testi — ikisi de aynı gerekçeyle prod CA'dan kaçınıyor.

`frontend/Caddyfile`'ın en başına, geçici olarak ekle:

```
{
	acme_ca https://acme-staging-v02.api.letsencrypt.org/directory
}
```

**Bu build, gerçek tepe bellek ölçümünü yapacağın build — atlamadan önce ikinci bir terminal aç**
ve `docker compose build`'i başlatmadan hemen önce orada şunu çalıştır:

```bash
while true; do date; free -m; echo "---"; sleep 1; done | tee ~/build-memory-log.txt
```

Sonra ilk terminalde:

```bash
docker compose build
docker compose up -d
```

Build bitince ikinci terminaldeki döngüyü `Ctrl+C` ile durdur.
`grep -A 2 "Mem:" ~/build-memory-log.txt | sort -k3 -n -r | head -5` ile en yüksek `used` değerini
bul — bu, A0'daki "Ölçülen gerçek build tepe değeri" satırına yazılacak sayı. **Bu adımı atlama**
— A0'da açıkça işaretlendiği gibi bu proje ölçeğinde bu değer yerelde hiç ölçülemedi, tek fırsat
burası.

**Kanıt:** `docker compose ps` → üç servis de `Up` (backend ve caddy `healthy` durumunda,
postgres'in kendi healthcheck'i zaten var). Build sırasında ölçülen tepe `used` değeri, sunucunun
toplam RAM'inin (4096 MB) altında kalmalı — swap'a taşmış olması (yani `Swap:` satırındaki
`used` değerinin 0'dan büyük çıkması) tek başına felaket değil ama "ne kadar yakın gittiğimizi"
gösterir, not al.

**Ek kanıt — ATLAMA, beş saniyelik bir kontrol:** Yerel migration provası (Ön Koşul bölümü)
zincirin GEÇERLİ olduğunu kanıtladı, ama sunucudaki bu ilk kalkış zincirin GERÇEK donanımdaki
İLK çalıştırılışı — bunu ayrıca doğrula:
```bash
docker compose exec postgres psql -U postgres -d appointment_db \
  -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank;"
```
**Beklenen:** 14 satır, `1`'den `14`'e kadar, hepsinin `success` sütunu `t`.

## A8.1 — Disk temizliği (her build'den sonra rutin)

Build cache ve eski (dangling) image'lar aylar içinde 40 GB'lık diski doldurabilir — disk
uyarısına (Bölüm B) güvenip bunu ihmal etme, her deploy'un sonuna ekle:

```bash
docker image prune -f
```

**Kanıt:** `docker system df` → `Images` satırındaki `RECLAIMABLE` sütunu düşük kalmalı,
zamanla büyümemeli.

## A9. Doğrulama turu

Her madde: komut + beklenen çıktı. `[SUNUCUDA DOĞRULANACAK]` etiketi olan maddeler yerelde
**kanıtlanamayan**, sadece burada gerçek anlam kazanan testler.

- [ ] **`[SUNUCUDA DOĞRULANACAK]` Postgres port kapalı.** Sunucudan (host'un kendisinden,
  container içinden değil):
  ```bash
  psql -h localhost -p 5432
  ```
  **Beklenen:** `connection refused` ya da benzeri bir hata. Bağlanabiliyorsan `docker-compose.yml`'i
  tekrar kontrol et — `postgres` servisinde bir `ports:` satırı sızmış olabilir.

- [ ] **Fotoğraf kalıcılığı — gerçek Linux'ta ilk kez.** Uygulamaya gerçek bir işletme fotoğrafı
  yükle (panelden), sonra:
  ```bash
  docker compose down && docker compose up -d
  ```
  **Beklenen:** Fotoğraf hâlâ görünüyor, `403`/kırık görsel yok. (Bu makinede zaten test
  edildi ve named volume sayesinde izin hatası beklenmiyor — ama bu, GERÇEK bir Linux
  dosya-sistemi izin modeliyle ilk kez çalıştığı an, o yüzden yine de bak.)

- [ ] **Backend root değil.**
  ```bash
  docker compose exec backend id
  ```
  **Beklenen:** `uid=1000(appuser) gid=1000(appgroup)`.

- [ ] **`[SUNUCUDA DOĞRULANACAK]` HTTPS sertifikası + HSTS.** Tarayıcıda `https://randevumweb.com`
  aç. Staging CA kullandıysan tarayıcı "güvenli değil" uyarısı verecek — BU AŞAMADA BEKLENEN,
  devam et (A10'da prod CA'ya geçilecek). Sertifikanın gerçekten sunulduğunu doğrula:
  ```bash
  curl -kI https://randevumweb.com
  ```
  **Beklenen:** `Strict-Transport-Security: max-age=31536000; includeSubDomains` header'ı
  yanıtta olmalı (`-k`, staging'in güvenilmeyen sertifikasını görmezden gelmek için).

- [ ] **`[SUNUCUDA DOĞRULANACAK]` `www` yönlendirmesi.**
  ```bash
  curl -kI https://www.randevumweb.com
  ```
  **Beklenen:** `HTTP/2 301` (veya `308`) + `location: https://randevumweb.com/` header'ı —
  hata sayfası değil, apex'e temiz bir yönlendirme. `www` için de bir sertifika sunulduğundan
  emin ol (`-k` staging sertifikasını görmezden gelmek için, prod CA'ya geçtikten sonra `-k`
  olmadan da dene).

- [ ] **`[SUNUCUDA DOĞRULANACAK]` Deep-link F5.** Tarayıcıda doğrudan `https://randevumweb.com/randevularim`
  adresine git (linke tıklamadan, adres çubuğuna yazıp Enter), sayfa açılınca F5 bas.
  **Beklenen:** `404` değil, sayfa yeniden yükleniyor ve React uygulaması render oluyor.

- [ ] **Actuator kapalı uçlar.**
  ```bash
  curl -k -o /dev/null -w "%{http_code}\n" https://randevumweb.com/actuator
  curl -k -o /dev/null -w "%{http_code}\n" https://randevumweb.com/actuator/env
  curl -k -o /dev/null -w "%{http_code}\n" https://randevumweb.com/actuator/health
  ```
  **Beklenen:** İlk ikisi `401`, üçüncüsü `200`.

- [ ] **`[SUNUCUDA DOĞRULANACAK]` Gerçek istemci IP — bu, yerelde HİÇ kanıtlanamayan madde.**
  Telefonunu **WiFi'den çıkar**, mobil veriye geçir (farklı bir gerçek IP'den bağlanman şart),
  siteye gir, bir istek at (ör. işletme listesini aç). Sonra sunucuda:
  ```bash
  docker compose logs backend --tail 50 | grep -i "http-nio"
  ```
  ya da rate-limit ile ilgili bir log satırı ara. **Beklenen:** loglardaki IP telefonunun
  GERÇEK mobil veri IP'si ile eşleşmeli (telefonundan `curl ifconfig.me` ile kontrol edebilirsin).
  **`172.x.x.x` ile başlayan bir IP görürsen bu YANLIŞ** — o, Docker'ın kendi iç bridge IP'si,
  gerçek istemci IP'si değil; bu durumda Caddy'nin `X-Forwarded-For` göndermediği ya da
  `server.forward-headers-strategy=native`'in çalışmadığı anlamına gelir, ROADMAP 3.5'teki
  riskin gerçekleştiği andır.

- [ ] **`[SUNUCUDA DOĞRULANACAK]` Reboot sonrası kendiliğinden ayağa kalkma.**
  ```bash
  sudo reboot
  ```
  1-2 dakika bekle, tekrar SSH ile bağlan:
  ```bash
  cd /opt/randevum && docker compose ps
  ```
  **Beklenen:** Üç servis de `Up`, hiçbir manuel `docker compose up` komutu çalıştırmadan.
  (A6'daki `unless-stopped` düzeltmesi burada test ediliyor.)

## A10. Staging'den production CA'ya geçiş

**Manuel sertifika temizliği GEREKMİYOR — Caddy bunu kendi başına doğru yapıyor.** Caddy,
sertifikaları `<data>/certificates/<ca-endpoint>/<domain>` yoluna, CA'nın kendi adresine göre
AYRI klasörlerde saklıyor — staging (`acme-staging-v02.api.letsencrypt.org-directory/...`) ve
production (`acme-v02.api.letsencrypt.org-directory/...`) sertifikaları hiç çakışmıyor
([Caddy Community](https://caddy.community/t/differences-between-acme-ca-and-cert-issuer/24672)).
`acme_ca` satırını kaldırıp Caddy'nin varsayılan (production) CA'sına dönünce, Caddy o yeni
namespace'te hiç sertifika bulamayıp KENDİLİĞİNDEN prod CA'dan taze bir tane istiyor —
`caddy_data` volume'unu silmene gerek yok, eski staging sertifikası orada durur ama hiç
kullanılmaz.

Yukarıdaki doğrulama turu geçtiyse, `frontend/Caddyfile`'ın başına eklediğin `acme_ca` bloğunu
**kaldır**, sonra:

```bash
docker compose build caddy
docker compose up -d caddy
```

**Kanıt — sertifikanın GERÇEKTEN production CA'dan geldiğini doğrula, sadece "kilit yeşil mi"
diye bakma:**
```bash
echo | openssl s_client -connect randevumweb.com:443 -servername randevumweb.com 2>/dev/null \
  | openssl x509 -noout -issuer
```
**Beklenen:** `issuer=C=US, O=Let's Encrypt, CN=R...` (gerçek bir intermediate adı, ör. `R10`,
`R11`, `E5` vb.) — **`STAGING` veya `Fake LE Intermediate` GEÇMEMELİ**, geçiyorsa hâlâ staging
sertifikası servis ediliyor demektir, `docker compose logs caddy` ile ACME denemesinin gerçekten
tetiklendiğini kontrol et. Tarayıcıda da `https://randevumweb.com` — "güvenli değil" uyarısı YOK,
kilit simgesi normal.

## A11. Karar: `/actuator/health` dışarıda kalsın mı? — ✅ ONAYLANDI, açık kalıyor

Gerekçe: `show-details=never` sadece gövdeyi (`components`) gizliyor, HTTP durum kodu yine de
DB durumuna göre değişiyor — canlı doğrulandı, Postgres durdurulunca `/actuator/health` **503**
+ `{"status":"DOWN"}` döndü. Dışarı kapalı olsaydı dış bir monitor'ün bunu fark etmesinin yolu
kalmazdı. `/` (statik ana sayfa) DEĞİL bu endpoint izlenecek — Caddy ayakta kaldığı sürece `/`
backend/Postgres tamamen ölse bile 200 döner.

## A12. Uptime monitor kurulumu — 3.8a'da, 3.8b'de DEĞİL

**Önemli sıra düzeltmesi:** Aşağıdaki "İlk 48 Saat" geçiş kriteri "kesintisiz 48 saat uptime"
şartı koşuyor — bu monitor 3.8b'ye ertelenirse 3.8a'yı hiç bitiremezsin (3.8a'yı bitirmek için
3.8b'de kurulacak bir aracın 48 saatlik verisine ihtiyacın olurdu, kendi kendini bloke eden bir
döngü). Bu yüzden uptime monitor'ü **şimdi, burada** kur — yedekleme ve dead-man's switch
(Bölüm B'nin geri kalanı) hâlâ 3.8b'de.

- [ ] UptimeRobot ya da healthchecks.io'da ücretsiz bir hesap aç.
- [ ] Yeni bir HTTP(S) monitörü `https://randevumweb.com/actuator/health` adresine, 5 dakikalık
  kontrol aralığıyla kur.
- [ ] Bildirim e-postanı ekle.

**Kanıt:** Monitör panelinde ilk kontrol **`UP`** (yeşil) görünmeli. İstersen kasıtlı olarak
`docker compose stop backend` ile kısa bir kesinti yaratıp (birkaç dakika içinde geri
`docker compose start backend`) monitörün gerçekten `DOWN` bildirimi gönderdiğini de görebilirsin
— bu, "İlk 48 Saat" penceresi başlamadan ÖNCE yapılırsa sayacı bozmaz.

**3.8a burada biter.** Yukarıdaki her kutucuk işaretlenmeden 3.8b'ye geçilmez.

---

# BÖLÜM B — Yedekleme, restore provası, izleme (3.8b)

**Ön koşul: Bölüm A'nın tüm kutucukları işaretli olmalı.**

## B1. Yedekleme scripti — kapsam

`pg_dump` YETMEZ — `business_photo_storage` named volume'u da yedeğe dahil olmalı (DB restore
edilip fotoğraf dizini unutulursa kırık resim linkleriyle bir sistem kalır).

- Postgres: `docker compose exec postgres pg_dump -U postgres appointment_db | gzip`
- Fotoğraflar: named volume olduğu için doğrudan `rsync` ÇALIŞMAZ — küçük bir yardımcı
  container'ın volume'u mount edip tar'laması gerekiyor:
  ```bash
  docker run --rm -v randevum_business_photo_storage:/data -v $(pwd)/backup:/backup \
    alpine tar czf /backup/photos-$(date +%F).tar.gz -C /data .
  ```

## B2. Yedek hedefi — AYNI SUNUCUDA OLMAYACAK

Yedek, sunucunun kendi diskinde dururken sunucu ölürse (disk arızası, hesap iptali, VPS
sağlayıcının kendi sorunu) yedek de ölür — bu bir yedek değil, yanılsama. `rclone` ile
**Cloudflare R2** (10 GB ücretsiz kota) veya Backblaze B2'ye gönderilmeli.

## B3. Cron + dead-man's switch

- Gecelik cron: `pg_dump` + foto arşivi + `rclone copy` ile R2'ye.
- healthchecks.io'da bir "check" oluştur, cron'un sonuna `curl https://hc-ping.com/<uuid>`
  ekle. Cron başarıyla biterse ping gider; ping belirlenen sürede gelmezse healthchecks.io
  alarm gönderir. **Bu olmadan sessizce durmuş bir yedekleme fark edilmez.**

## B4. Kabul kriteri — "script yazıldı" YETERLİ DEĞİL

Bu madde şu ADIM fiilen yapılıp kanıtlanmadan işaretlenmeyecek. **Restore, canlı sistemin
ÜZERİNE değil, tamamen AYRI bir stack'e yapılır** (yeni bir geçici VPS, ya da en azından bu
sunucuda farklı bir dizin + farklı Docker Compose proje adıyla ayrı volume'lar) — amaç "elimizde
gerçekten bağımsız çalışan bir yedek var mı" sorusuna cevap vermek, mevcut prod'u riske atmadan.

- [ ] Test verisiyle gerçek bir randevu + gerçek bir işletme fotoğrafı oluştur.
- [ ] Yedek scriptini elle bir kez çalıştır, R2'ye gerçekten yüklendiğini gör.
- [ ] **Saat tut** (`date` komutuyla başlangıç/bitiş): ayrı, boş bir stack'e o yedeği geri
  yükle — sıfırdan container ayağa kaldırmaktan, `pg_dump` restore'undan, foto volume'unu
  doldurmaktan, uygulamanın erişilebilir hâle gelmesine kadar geçen SÜREYİ ölç.
- [ ] **Kanıt:** restore edilen sistemde o randevu VE o fotoğraf görünüyor mu? İkisi de
  görünmeden bu madde kapanmaz.
- [ ] **Ölçülen restore süresi buraya yazılacak:** `___ dakika`. "Yedek restore edilebiliyor"
  yeterli değil — gerçek bir kesinti anında "ne kadar sürede ayağa kaldırabiliyorum" bilgisi
  olmadan bu sayı bir işe yaramaz.
- [ ] **Faz 3.9 gerçekleştirildikten SONRA, bu adım restore prosedürüne eklenecek:** yedek
  anonimleştirmeden ÖNCEKİ bir ana aitse, restore silinen PII'yi sessizce geri getirir.
  Restore'un SON adımı olarak, `anonymizedAt IS NOT NULL` olan kullanıcıları bulup
  `name`/`surName`/`email`/`phone`/`password`'ini yeniden scrub eden bir script/adım
  çalıştırılmalı (bkz. ROADMAP.md, 3.9 hesap silme akışı). Bu madde 3.9 yazılana kadar
  boş bir hatırlatma olarak kalıyor.

## B5. İzleme — kalan ikili (uptime monitor zaten A12'de kuruldu)

- [ ] Disk doluluk uyarısı — basit bir cron + `df` eşiği + mail, ya da sağlayıcının kendi paneli.
- [ ] B3'teki dead-man's switch aktif ve en az bir kez gerçek bir alarmla (kasıtlı olarak cron'u
  durdurup) test edilmiş olmalı.

---

# ROLLBACK

Deploy sonrası bir şey bozulursa:

## Adım 0 — ÖNCE teşhis et, "bekleyelim düzelir mi" YOK

```bash
docker compose logs backend --tail 100 | grep -i "flyway\|migration"
```

**Flyway hatası görüyorsan ("FlywayMigrateException", "Migration ... failed") beklemek bir
seçenek DEĞİL.** Ön koşul bölümünde canlı kanıtlandı: bozuk bir migration'da uygulama KALICI
olarak ölü kalıyor, `restart: unless-stopped` aynı hatayı sonsuza kadar tekrar dener — "biraz
bekleyelim düzelir mi" davranışı asla düzelmez, sadece downtime'ı uzatır. Flyway hatası
görüyorsan **doğrudan aşağıdaki "Gerçek rollback" adımına geç**, container'ın kendi kendine
toparlanmasını bekleme.

Flyway hatası YOKSA (uygulama başladı ama başka bir şey bozuk — ör. bir endpoint 500 veriyor,
beklenmeyen bir davranış var), asıl soruna göre karar ver — rollback tek çözüm olmayabilir.

## Önleyici adım (HER deploy'dan önce yapılır, rollback'in kendisi değil)

```bash
cd /opt/randevum
PREV_SHA=$(git rev-parse --short HEAD)
docker tag randevum-backend:latest randevum-backend:$PREV_SHA
docker tag randevum-caddy:latest randevum-caddy:$PREV_SHA
git pull
docker compose build
docker compose up -d
```

Bu sırayla: mevcut çalışan image'ı SHA'sıyla etiketler, SONRA yeni kodu çeker ve build eder.
`latest` etiketiyle build etmek (registry olmadan, salt yerel image'larla) tek başına rollback
sağlamaz — bir önceki `latest` üzerine yazılır, geri dönecek bir şey kalmaz. Bu yüzden SHA
etiketleme, YENİ build'den ÖNCE yapılmalı.

## Gerçek rollback

```bash
cd /opt/randevum
docker tag randevum-backend:$PREV_SHA randevum-backend:latest
docker tag randevum-caddy:$PREV_SHA randevum-caddy:latest
docker compose up -d --no-build
```

**Kanıt:** `docker compose ps` → servisler `Up`, uygulama önceki davranışına dönmüş olmalı.

## Sınır — açıkça bil

Bu SADECE kod/image seviyesinde geri dönüş sağlar. **Flyway migration'ları geriye alınmıyor**
(bu projede hiç yapılmadı, yapılması da genelde önerilmez). Bozulan deploy bir DB şema
değişikliği içeriyorsa, image'ı eski SHA'ya döndürmek YETMEZ — eski kod, yeni şemayla
uyumsuz kalabilir. Böyle bir durumda rollback'ten önce migration'ın geri alınabilir olup
olmadığı ayrıca değerlendirilmeli; genel kural olarak "geriye dönük uyumlu migration yaz"
disiplini (yeni kolon NULL'a izin versin, eski kod onu hiç kullanmasın gibi) bu riski en
baştan azaltır — bu artık CLAUDE.md'de kalıcı bir kural (bkz. "Migration'lar geriye uyumlu
yazılmalı").

---

# DEPLOY SONRASI İLK 48 SAAT

Monitoring (A12) sadece "ölü mü canlı mı" sorusuna cevap verir — "garip bir şey mi oluyor"
sorusuna cevap vermez. 404 yağmuru, tuhaf user-agent'lar, yavaş sorgular hiçbir alarm
tetiklemeden günlerce sürebilir. İlk 48 saat elle bakılacak.

## Bu pencerede GERÇEK işletme verisi YOK

Sistem ayakta olacak ama **yedeklemesiz** — 3.8b (restore provası) henüz kanıtlanmadı. Bu
pencerede sisteme giren HERHANGİ bir veri, restore edilebilirliği kanıtlanana kadar korumasız.
- [ ] Sadece kendi test hesaplarınla dolaş (kayıt, randevu, foto yükleme dahil).
- [ ] **Tanıdığın bir işletmeye "gel dene" DEME** — 3.8b'nin B4 kabul kriteri (restore edilip
  randevu+foto geldiğini gösterme) kanıtlanmadan gerçek bir işletmenin verisini bu sisteme
  koydurma. Restore provası geçmeden buraya giren gerçek veri, bir kesinti anında geri
  getirilemez.

## İlk gün — elle log izleme

```bash
docker compose logs -f backend
```

Birkaç kez (sabah, öğlen, akşam), birkaç dakikalığına gerçek trafiği canlı izle. Aranacak
şeyler: beklenmeyen `404` yığılması (kırık bir link mi paylaşılıyor, tarayan bir bot mu var),
alışılmadık `User-Agent` değerleri, `/actuator`/`/wp-admin`/`.env` gibi yollara yapılan
otomatik tarama denemeleri (zararsız ama fail2ban'ın bunları yakaladığını görmek için iyi bir
işaret), ve tekrar eden `WARN`/`ERROR` satırları.

## `fail2ban` loglarını kontrol et

```bash
sudo fail2ban-client status sshd
sudo journalctl -u fail2ban --since "1 hour ago"
```

İlk saatlerde ne kadar hızlı taranmaya başlandığını gör — bir sunucu internete açıldıktan
dakikalar içinde otomatik SSH tarama denemeleri almaya başlar, bu normaldir; `fail2ban`'ın
bunları gerçekten yasakladığını (`Currently banned` sayısının 0'dan büyük olması) görmek,
A1.5'teki kurulumun gerçekten işlediğinin kanıtı.

## Ne zaman "stabil" sayılır — 3.8b'ye geçiş kriteri

48 saatlik sayaç, A12'de uptime monitor kurulup ilk `UP` göründüğü andan başlar (A10'da prod
CA'ya zaten geçilmiş olacağı için bu, gerçek prod ortamının ilk 48 saati). Aşağıdakilerin HEPSİ,
**kesintisiz 48 saat** boyunca doğru olmalı:

- [ ] Uptime monitor (`/actuator/health`) 48 saat boyunca **tek bir düşüş bildirmedi**.
- [ ] `docker compose ps` — üç servis de hâlâ `Up`, hiçbiri restart döngüsüne girmemiş
  (`docker compose ps` çıktısındaki `STATUS` sütununda "Restarting" görülmemiş olmalı; ayrıca
  `docker inspect randevum-backend-1 --format '{{.RestartCount}}'` ile sayı kontrol edilebilir).
- [ ] En az bir kez, gerçek bir cihazdan (kendi telefonun) uçtan uca gerçek bir akış denendi:
  kayıt, giriş, işletme arama, randevu talebi — hepsi hatasız çalıştı.
- [ ] `df -h` — disk kullanımı 48 saatte anormal bir sıçrama göstermedi (log/build cache
  birikimi kontrolsüzse burada görünür).
- [ ] **HSTS `max-age`'i yükselt.** 48 saat sorunsuz geçtiyse, `frontend/Caddyfile`'daki
  `Strict-Transport-Security "max-age=300"` satırını `"max-age=31536000; includeSubDomains"`e
  çevir, `docker compose build caddy && docker compose up -d caddy` ile yeniden dağıt. Bu 48
  saatlik pencere TAM OLARAK bu yükseltmenin GÜVENLE yapılabileceği an — daha erken yükseltip
  sonra bir HTTPS sorunu çıkarsa, uzun `max-age` zaten cache'lenmiş tarayıcılarda geri
  dönülemez bir duruma yol açar.
- [ ] Yukarıdaki log/fail2ban incelemesi en az bir kez yapıldı, hiçbir açıklanamayan davranış
  bulunmadı.

Bu altı madde de sağlanmadan **3.8b'ye (yedekleme/izleme) geçilmez** — henüz
stabil olmayan bir sistemin yedeğini almanın bir anlamı yok, önce sistemin kendisinin ayakta
kaldığından emin olunur.
