-- ============================================================
--  V17 — Isletme fotograflari tek kolondan ayri tabloya tasiniyor.
--
--  Kapak fotografi (photo_key kolonu, V12) su ana kadar tek bir
--  fotografi destekliyordu. Coklu galeri (en fazla 5, bkz.
--  app.business-photo.max-photos-per-business) icin ayri bir tablo
--  gerekiyor -- Staff/ServiceItem ile ayni desen.
--
--  EXPAND-CONTRACT: businesses.photo_key kolonu BILEREK SILINMIYOR,
--  sadece artik uygulama tarafindan okunmuyor/yazilmiyor (Business
--  entity'sinden de kaldirilacak). Kolonun gercekten kaldirilmasi
--  ayri, SONRAKI bir migration'da yapilacak -- ayni deploy'da hem
--  yazan kodu degistirip hem kolonu silmek, NOTLAR.md'deki "Migration'lar
--  geriye uyumlu yazilmali" kuralini ihlal ederdi (bkz. CLAUDE.md karar
--  tablosu).
--
--  UNIQUE(business_id, display_order) KONULMADI -- silme sonrasi
--  siralamada bosluk kalmasi (0, 2, 3 gibi) normal, yeniden numaralama
--  mantigi yazmaya gerek yok. Eszamanlilik guvenligi DB constraint'i
--  degil, servis katmanindaki pessimistic lock ile saglaniyor (bkz.
--  BusinessPhotoService, ayni BusinessService.swapPhotoKey deseni).
--
--  ON DELETE CASCADE: Business satirlari bugun hic hard-delete
--  edilmiyor (hesap silme sadece askiya aliyor/anonimlestiriyor,
--  grep ile dogrulandi) -- yine de ileride bir admin ozelligi
--  eklenirse oksuz satir kalmasin diye savunma amacli.
-- ============================================================

CREATE TABLE business_photos (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    photo_key VARCHAR(64) NOT NULL,
    display_order SMALLINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_business_photos_business_id ON business_photos (business_id, display_order);

-- Backfill: mevcut tek kapak fotografi olan isletmeler yeni tabloya
-- display_order=0 (kapak) olarak kopyalaniyor. photo_key NULL olan
-- isletmeler (hic fotograf yuklememis) hic satir almiyor -- bu dogru,
-- "fotografsiz" durumu bos koleksiyonla ayni anlama geliyor.
INSERT INTO business_photos (business_id, photo_key, display_order)
SELECT id, photo_key, 0
FROM businesses
WHERE photo_key IS NOT NULL;
