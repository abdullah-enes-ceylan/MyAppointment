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
çalışıyor, ikisi de geçici bellek sıçraması yapabilir — bu, `docker stats`'ın yakalayamadığı
geçici BuildKit süreçleri olduğu için bu oturumda ölçülemedi.

Bu proje ölçeğinde (tek Spring Boot uygulaması, tek React SPA — 131 Java kaynak dosyası, 152
frontend modülü, ~558 KB'lık tek bir JS bundle) build'in 1-2 GB'ı aşması beklenmez, ama **build
sunucuda yapılacağı** (CI/registry altyapısı yok, kurmak bu ölçekte orantısız karmaşıklık olurdu)
için ucuz bir sigorta gerekiyor: **2 GB swap dosyası zorunlu** (aşağıda A2). Bunu atlama.

Sunucuyu bundan küçük almayı düşünüyorsan durup tekrar konuşalım — 4 GB + swap altına inmek bu
planın varsayımlarını geçersiz kılar.

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
```

Bu adımdan sonra **çıkış yapıp tekrar SSH ile bağlan** (grup üyeliğinin geçmesi için).

**Kanıt:** `docker run hello-world` → "Hello from Docker!" mesajı, `sudo` olmadan çalışmalı.

## A3. DNS — Caddy'nin ACME denemesinden ÖNCE

Alan adı sağlayıcında (Cloudflare, vs.) bir **A kaydı** ekle: `randevum.com` (ya da gerçek
domain'in) → sunucunun IP'si. Alt domain de istiyorsan (`www`) ayrı bir A kaydı.

**Kanıt:** Kendi makinenden:
```bash
dig +short randevum.com
```
→ sunucunun IP'si dönmeli. Dönmüyorsa veya eski bir IP dönüyorsa **bekle** — DNS yayılması
dakikalar ile saatler arasında sürebilir, yayılmadan bir sonraki adıma (A6, ACME) geçme. Prod
CA'nın (A6'dan sonra) saatte 5 hata limiti var, yayılmayı beklemeden denemek bu limiti gereksiz
yere tüketir.

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
sed -i "s|BURAYA_GERCEK_DOMAIN_YAZ|randevum.com|g" .env
chmod 600 .env
```

`CORS_ALLOWED_ORIGINS` değerini gerçek domain'inle (`https://randevum.com`) elle kontrol et —
yukarıdaki `sed` `DOMAIN` ve `CORS_ALLOWED_ORIGINS`'i aynı anda değiştiriyor, ikisinin de doğru
göründüğünden emin ol.

**Kanıt:**
```bash
cat .env
```
→ `JWT_SECRET` ve `POSTGRES_PASSWORD` rastgele karakter dizileri olmalı, **dev'deki
`xmcU1nTSzObx7z9SDUvOZxEGVSyE...` değeri OLMAMALI**. `ls -la .env` → izin `-rw-------` (600)
olmalı.

## A6. `restart: unless-stopped` — kontrol et, eksikse ekle

Şu an `docker-compose.yml`'de sadece `backend` ve `caddy`'de `restart: on-failure` var,
`postgres`'te HİÇ yok — ve `on-failure` bir VPS reboot'undan sonra container'ları otomatik
başlatmaz (sadece çöken bir container'ı yeniden dener). Deploy etmeden önce üç servise de
`restart: unless-stopped` ekle (`docker-compose.yml`'de `restart:` satırlarını bul, değiştir).

**Kanıt:** `grep -n "restart:" docker-compose.yml` → üç satır da `unless-stopped` olmalı.

## A7. Rollback için image adlandırması

`docker-compose.yml`'deki `backend` ve `caddy` servislerine sabit bir `image:` adı ekle (yoksa
Docker, dizin adından otomatik bir isim üretir — sunucuda dizin adı farklıysa etiketleme
tutarsız olur):

```yaml
  backend:
    image: randevum-backend
    build: ...
  caddy:
    image: randevum-caddy
    build: ...
```

**Kanıt:** `grep -A 1 "^  backend:\|^  caddy:" docker-compose.yml` → her ikisinde de `image:`
satırı görünmeli.

## A8. İlk deploy — Let's Encrypt STAGING CA ile

Gerçek (production) CA'nın saatte 5 başarısız deneme sınırı var. Caddyfile/DNS ayarını ilk
seferde doğru kurmayabilirsin — staging CA'da bu sınıra hiç takılmazsın, sertifika "güvensiz"
görünür ama mekanizmanın çalıştığını kanıtlamak için yeterli.

`frontend/Caddyfile`'ın en başına, geçici olarak ekle:

```
{
	acme_ca https://acme-staging-v02.api.letsencrypt.org/directory
}
```

```bash
docker compose build
docker compose up -d
```

`docker compose build` birkaç dakika sürebilir (Maven + Node) — bu sırada başka bir terminalden
`free -h`'ı izlemek istersen swap'ın devreye girip girmediğini görürsün, bu normal.

**Kanıt:** `docker compose ps` → üç servis de `Up` (backend ve caddy `healthy` durumunda,
postgres'in kendi healthcheck'i zaten var).

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

- [ ] **`[SUNUCUDA DOĞRULANACAK]` HTTPS sertifikası + HSTS.** Tarayıcıda `https://randevum.com`
  aç. Staging CA kullandıysan tarayıcı "güvenli değil" uyarısı verecek — BU AŞAMADA BEKLENEN,
  devam et (A10'da prod CA'ya geçilecek). Sertifikanın gerçekten sunulduğunu doğrula:
  ```bash
  curl -kI https://randevum.com
  ```
  **Beklenen:** `Strict-Transport-Security: max-age=31536000; includeSubDomains` header'ı
  yanıtta olmalı (`-k`, staging'in güvenilmeyen sertifikasını görmezden gelmek için).

- [ ] **`[SUNUCUDA DOĞRULANACAK]` Deep-link F5.** Tarayıcıda doğrudan `https://randevum.com/randevularim`
  adresine git (linke tıklamadan, adres çubuğuna yazıp Enter), sayfa açılınca F5 bas.
  **Beklenen:** `404` değil, sayfa yeniden yükleniyor ve React uygulaması render oluyor.

- [ ] **Actuator kapalı uçlar.**
  ```bash
  curl -k -o /dev/null -w "%{http_code}\n" https://randevum.com/actuator
  curl -k -o /dev/null -w "%{http_code}\n" https://randevum.com/actuator/env
  curl -k -o /dev/null -w "%{http_code}\n" https://randevum.com/actuator/health
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

Yukarıdaki doğrulama turu geçtiyse, `frontend/Caddyfile`'ın başına eklediğin `acme_ca` bloğunu
**kaldır**, sonra:

```bash
docker compose build caddy
docker compose up -d caddy
```

**Kanıt:** Tarayıcıda `https://randevum.com` — artık "güvenli değil" uyarısı YOK, kilit simgesi
yeşil/normal, sertifika veren "Let's Encrypt" (tarayıcının sertifika detayından kontrol
edilebilir).

## A11. Karar: `/actuator/health` dışarıda kalsın mı?

- [ ] **Onaylıyorum, açık kalsın** — dış uptime-monitor (aşağıda 3.8b) buna ping atacak.
- [ ] **Hayır, kapatıyorum** — `management.endpoints.web.exposure.include=health` satırını
  kaldır, Caddyfile'daki `/actuator` handle bloğundan health de dahil her şey default-deny'e
  düşer, izleme başka bir yolla (ör. Docker healthcheck'i harici bir script'le okuma) kurulur.

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

Bu madde şu ADIM fiilen yapılıp kanıtlanmadan işaretlenmeyecek:

- [ ] Test verisiyle gerçek bir randevu + gerçek bir işletme fotoğrafı oluştur.
- [ ] Yedek scriptini elle bir kez çalıştır, R2'ye gerçekten yüklendiğini gör.
- [ ] **Ayrı, boş bir stack'e** (yeni bir VPS'e ya da aynı sunucuda volume'ları tamamen silip
  sıfırdan) o yedeği geri yükle.
- [ ] **Kanıt:** restore edilen sistemde o randevu VE o fotoğraf görünüyor mu? İkisi de
  görünmeden bu madde kapanmaz.

## B5. İzleme — minimum üçlü

- [ ] UptimeRobot/healthchecks.io: `/actuator/health`'e (ya da A11'de kapatıldıysa alternatif
  bir mekanizmaya) 5 dakikada bir ping, düşerse mail.
- [ ] Disk doluluk uyarısı — basit bir cron + `df` eşiği + mail, ya da sağlayıcının kendi paneli.
- [ ] B3'teki dead-man's switch aktif ve en az bir kez gerçek bir alarmla (kasıtlı olarak cron'u
  durdurup) test edilmiş olmalı.

---

# ROLLBACK

Deploy sonrası bir şey bozulursa:

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
baştan azaltır.
