-- ============================================================
--  V11 — Cevaplanmayan randevu taleplerinin zaman asimina ugramasi.
--
--  SORUN: talep PENDING doguyor ve isletme cevaplayana kadar oyle
--  kaliyor. Bu iki sey uretiyordu: (1) cevaplanmayan talep slotu
--  baska musteriye kapatiyor, (2) otomatik tamamlama job'i sadece
--  APPROVED -> COMPLETED yaptigi icin hic cevaplanmamis bir talep,
--  randevu saati gecse bile sonsuza kadar PENDING kalip istek
--  kutusunda birikiyor.
--
--  Dusme ani hesabi uygulamada (AppointmentExpiryPolicy) -- oradaki
--  aciklama kuralin neden iki parcali oldugunu anlatiyor.
-- ============================================================

-- 1) Talebin ne zaman olusturuldugu. Kural "randevu saati - talep ani"
--    penceresine dayaniyor, bu bilgi simdiye kadar hic tutulmuyordu.
--
--    DEFAULT now() SADECE mevcut satirlari doldurmak icin; hemen
--    ardindan kaldiriliyor. Sebep: DEFAULT kalsaydi degeri Postgres'in
--    saati uretirdi, oysa uygulamanin kendi saat kaynagi var
--    (TimeConfig'teki Clock bean'i). Iki saat kaynaginin ayni kolonu
--    beslemesi, uygulama ile veritabani farkli saat diliminde
--    calistiginda sessizce kaymis degerler uretir. DEFAULT'u kaldirinca
--    yeni satirlarin degeri DAIMA uygulamadan gelmek zorunda kaliyor --
--    disiplinle degil, yapisal olarak.
ALTER TABLE appointments ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE appointments ALTER COLUMN created_at DROP DEFAULT;

-- 2) Yeni durum. REJECTED kullanilmiyor: "isletme seni reddetti" demek
--    yanlis bilgi olurdu, isletme sadece gormemis. Ayrica ileride
--    isletmeye "gecen ay N talebi cevapsiz biraktin" metrigi verilebilir.
--    Postgres'te CHECK dogrudan ALTER edilemiyor -- V3'teki (NO_SHOW)
--    ayni DROP + ADD deseni.
ALTER TABLE appointments DROP CONSTRAINT appointments_status_check;
ALTER TABLE appointments ADD CONSTRAINT appointments_status_check CHECK (status IN
    ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED', 'NO_SHOW', 'EXPIRED'));

-- NOT: slot engelleme ve DB unique index'leri degistirilmedi, gerekmiyor.
-- Ikisi de "status IN ('PENDING','APPROVED')" uzerinden calisiyor; EXPIRED
-- bu listede olmadigi icin dusen talep hem uygulama hem veritabani
-- seviyesinde slotu kendiliginde serbest birakiyor.
--
-- BACKFILL SONUCU: mevcut satirlar created_at = migration ani aliyor,
-- yani gercek talep zamanlari kayboluyor. Bilincli kabul: bunlar
-- gelistirme/seed verisi, uretim veritabani ise henuz hic olusturulmadi
-- (deploy edilmemis), dolayisiyla gercek veri etkilenmiyor. Tahmini bir
-- deger (or. appointment_date - ortalama pencere) uretmek daha kotu
-- olurdu: gercek gorunen ama uydurma bir zaman, ileride hangi satirin
-- gercek oldugunu ayirt edilemez hale getirirdi.
