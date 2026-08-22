-- Faz 2.1: durum makinesine NO_SHOW eklendi ("musteri gelmedi" -- isletme
-- sahibinin onaylanmis bir randevuyu manuel isaretledigi durum). V1'deki
-- appointments_status_check kismi CHECK'i genisletiyoruz -- Postgres'te
-- CHECK constraint'i dogrudan ALTER edilemiyor, DROP + yeniden ADD gerekiyor.
ALTER TABLE appointments DROP CONSTRAINT appointments_status_check;

ALTER TABLE appointments ADD CONSTRAINT appointments_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED', 'NO_SHOW'));
