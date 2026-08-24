-- ============================================================
--  V8 — "Onaylı işletme" rozeti. Şimdilik SADECE veritabanı/seed
--  üzerinden set edilebiliyor -- owner'ın kendi kendini onaylı
--  işaretleyebileceği bir uç noktaya BİLİNÇLİ OLARAK izin vermiyoruz
--  (BusinessRequest'e eklenmedi), gerçek bir admin onay akışı ileride
--  ayrı bir iş kalemi olarak eklenecek.
-- ============================================================

ALTER TABLE businesses ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE;
