-- ============================================================
--  V10 — "Kime hizmet veriliyor" bilgisi kategoriden ayrılıyor.
--
--  Önceden bu bilgi kategoriye gömülüydü (BARBER = erkek), ki bu üç
--  sorun üretiyordu: unisex salon kendini ifade edemiyor ve müşterisinin
--  yarısına görünmez oluyordu; "erkek kuaförü" hangi kategoriyi
--  seçeceğini bilemiyordu; erkek müşteri kadın kuaförüne yanlışlıkla
--  randevu isteği gönderebiliyordu (işletmenin istek kutusuna alakasız
--  talep düşüyordu). Detaylı gerekçe: ServedGender enum'ındaki açıklama.
--
--  Bu değişikliğin ŞİMDİ yapılmasının sebebi: veritabanında henüz sadece
--  seed verisi var, BARBER kayıtlarını çevirmek bedava. Beta'da gerçek
--  işletmeler kendi kategorilerini seçtikten sonra aynı değişiklik gerçek
--  veri taşıma işine dönüşürdü.
-- ============================================================

-- 1) Yeni sütun. DEFAULT 'UNISEX': mevcut satırlar NOT NULL kısıtını
--    ihlal etmeden dolsun. En kapsayıcı değer bilerek seçildi -- yanlış
--    tahminle bir işletmeyi olduğundan DAR göstermek, geniş göstermekten
--    daha zararlı olurdu (müşterisine görünmez olurdu).
ALTER TABLE businesses
    ADD COLUMN served_gender VARCHAR(255) NOT NULL DEFAULT 'UNISEX'
    CHECK (served_gender IN ('MALE', 'FEMALE', 'UNISEX'));

-- 2) Berberleri taşı. SIRA ÖNEMLİ: önce cinsiyeti işaretle, sonra
--    kategoriyi değiştir -- tersi olsaydı hangi satırların berber
--    olduğu bilgisi kaybolurdu.
UPDATE businesses SET served_gender = 'MALE' WHERE category = 'BARBER';
UPDATE businesses SET category = 'HAIRDRESSER' WHERE category = 'BARBER';

-- 3) Kategori CHECK kısıtını BARBER'sız yeniden kur. Postgres'te bir
--    CHECK doğrudan ALTER edilemiyor, V3'teki appointments_status_check
--    ile aynı DROP + ADD deseni.
ALTER TABLE businesses DROP CONSTRAINT businesses_category_check;
ALTER TABLE businesses ADD CONSTRAINT businesses_category_check CHECK (category IN
    ('HAIRDRESSER', 'BEAUTY_SALON', 'SPA_WELLNESS',
     'NAIL_STUDIO', 'MAKEUP_STUDIO', 'TATTOO_STUDIO'));
