-- ============================================================
--  V13 — Bildirim gonderim/idempotency gunlugu (Faz 3.4).
--
--  Bu tablo kanaldan BAGIMSIZ: hangi NotificationPort implementasyonu
--  (in-app, log, ileride SMS/e-posta) aktif olursa olsun her zaman
--  yazilir. Amaci "bu randevu icin bu tur bildirim daha once GERCEKTEN
--  gonderildi mi" sorusuna DB seviyesinde kesin bir cevap vermek --
--  ayni randevuya iki kez hatirlatma/uyari gitmesini engelleyen tek
--  mekanizma bu.
-- ============================================================

CREATE TABLE notification_log (
    id BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL REFERENCES appointments(id),
    notification_type VARCHAR(50) NOT NULL,
    recipient_user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('SENT', 'FAILED')),
    attempted_at TIMESTAMP NOT NULL,
    error_message VARCHAR(500)
);

-- Ayni (appointment_id, notification_type) icin SADECE tek bir SENT
-- satiri olabilir -- iki es zamanli calisma (ornegin scheduler'in iki
-- node'da ayni anda tetiklenmesi) ayni randevuyu ayni anda "henuz
-- gonderilmemis" gorup ikisi de gondermeye calissa bile, ikinci INSERT
-- bu index'i ihlal edip patlar. Randevu slot rezervasyonundaki
-- ux_appointments_active_slot ile BIREBIR ayni savunma deseni.
-- FAILED satirlarina bilerek kisit yok: birden fazla basarisiz deneme
-- (sonunda basarili olana kadar) normal ve zararsiz.
CREATE UNIQUE INDEX ux_notification_log_sent
    ON notification_log (appointment_id, notification_type)
    WHERE status = 'SENT';
