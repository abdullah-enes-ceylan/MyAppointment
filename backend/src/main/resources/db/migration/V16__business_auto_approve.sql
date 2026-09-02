-- ============================================================
--  V16 — Isletme basina "otomatik onay" anahtari.
--
--  Karar (bkz. CLAUDE.md karar tablosu, "Randevu istek mi, direkt mi"):
--  varsayilan davranis Istek Kutusu (PENDING) olarak kaliyor -- betada asil
--  risk benimsenme, esnafa "sen onaylamadan hicbir sey olmaz" diyebilmek
--  onemli. Bu anahtar, zaman icinde onay adiminin degerini kaybettigini
--  dusunen isletmeler icin ISTEGE BAGLI bir cikis: acarsa yeni randevu
--  talepleri PENDING yerine dogrudan APPROVED doguyor.
--
--  DEFAULT FALSE: mevcut butun isletmeler icin davranis birebir korunuyor,
--  bilinclilik gerektiren bir secim -- hicbir isletme sahibi bunu
--  farkinda olmadan acmis olamaz.
-- ============================================================

ALTER TABLE businesses ADD COLUMN auto_approve BOOLEAN NOT NULL DEFAULT FALSE;
