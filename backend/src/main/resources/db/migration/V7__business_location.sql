-- ============================================================
--  V7 — Faz 2.8: konum bazlı arama. latitude/longitude NULLABLE --
--  işletme sahibi konumunu henüz girmemiş olabilir (mevcut tüm test
--  verisi dahil), bu durumda o işletme "yakınımdakiler" sorgusunda
--  hiç eşleşmez (SQL BETWEEN NULL ile asla eşleşmez), hata vermez.
--
--  Composite index: LocationService'teki bounding-box ön filtresi
--  (WHERE latitude BETWEEN ... AND longitude BETWEEN ...) bu index'i
--  kullanabilsin diye. Gerçek mesafe (Haversine) hesabı Java tarafında
--  yapılıyor -- native SQL fonksiyonuna bağlı kalmamak, birim testi
--  yazabilmek ve ileride farklı bir DB motoruna (ör. test ortamında
--  H2) taşınabilirlik için (bkz. LocationService'teki açıklama).
-- ============================================================

ALTER TABLE businesses ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE businesses ADD COLUMN longitude DOUBLE PRECISION;

CREATE INDEX idx_businesses_location ON businesses (latitude, longitude);
