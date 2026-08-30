-- ============================================================
--  V12 — Isletme kapak fotografi icin tek kolon.
--
--  photo_key NULL: fotograf yuklemeyen isletme eski gradyan kapagi
--  gostermeye devam eder (bkz. plan "Isletme Kapak Fotografi" PR2).
--  Kart ve detay boyutlarinin dosya adlari bu key'den TURETILIYOR
--  ({key}-card.jpg, {key}-detail.jpg) -- ayri kolon gerekmiyor, ucuncu
--  bir boyut gerekirse yeni migration olmadan eklenebilir.
--
--  Deger UUID.randomUUID() (uzantisiz) -- VARCHAR(64) rahat siga.
-- ============================================================

ALTER TABLE businesses ADD COLUMN photo_key VARCHAR(64);
