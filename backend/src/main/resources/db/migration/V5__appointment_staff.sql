-- ============================================================
--  V5 — Faz 2.5: randevuyu personele baglama.
--
--  staff_id NULLABLE: personel sistemi kullanmayan (hic Staff eklememis)
--  bir isletmenin randevulari icin null kalir -- mevcut butun test
--  verisi ve personelsiz isletmeler icin davranis birebir korunuyor.
--
--  V1'deki tek partial unique index (business_id, appointment_date) IKIYE
--  BOLUNUYOR:
--    - staff_id NULL olan randevular hala business_id uzerinden korunur
--      (isletme capinda kapasite=1, eski davranis).
--    - staff_id DOLU olan randevular artik staff_id uzerinden korunur --
--      boylece ayni saatte FARKLI personellere randevu alinabilir, ki
--      "ayni saate 2 kisi alabilmeliyim" ihtiyacinin tam cozumu budur.
--  Tek index'i degistirmek (WHERE kosulunu genisletmek) yerine ikiye
--  bolmenin sebebi: partial unique index'te NULL degerler birbirinden
--  FARKLI sayilir (unique kisitini hic tetiklemez) -- yani staff_id'yi
--  duz bir sekilde (business_id yerine) tek index'e koysaydik, staff_id
--  NULL olan randevular arasinda ARTIK hicbir cakisma korumasi kalmazdi.
-- ============================================================

ALTER TABLE appointments ADD COLUMN staff_id BIGINT NULL;
ALTER TABLE appointments ADD CONSTRAINT fk_appointments_staff FOREIGN KEY (staff_id) REFERENCES staff (id);

-- staff_id ile filtrelenen/sorgulanan yerler icin (bkz. AppointmentRepository'deki
-- yeni staff bazli sorgular) -- FK sutunlarina Postgres otomatik index koymuyor.
CREATE INDEX idx_appointments_staff_date ON appointments (staff_id, appointment_date);

DROP INDEX ux_appointments_active_slot;

CREATE UNIQUE INDEX ux_appointments_active_slot_business ON appointments (business_id, appointment_date)
    WHERE status IN ('PENDING', 'APPROVED') AND staff_id IS NULL;

CREATE UNIQUE INDEX ux_appointments_active_slot_staff ON appointments (staff_id, appointment_date)
    WHERE status IN ('PENDING', 'APPROVED') AND staff_id IS NOT NULL;
