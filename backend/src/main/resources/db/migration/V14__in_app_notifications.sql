-- ============================================================
--  V14 — Uygulama ici bildirim kutusu (Faz 3.4, InAppNotificationAdapter).
--
--  notification_log (V13) ile KARISTIRILMAMALI: o tamamen icsel bir
--  muhasebe tablosu (idempotency), bu ise gercek bir urun ozelligi --
--  kullanicinin ileride frontend'de zil ikonundan gorecegi satirlar.
--  Kanal degisirse (ornegin SMS eklenirse) bu tablo hicbir sekilde
--  etkilenmez; sadece in-app kanali aktifken doluyor.
-- ============================================================

CREATE TABLE in_app_notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(200) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);

-- "kendi bildirimlerim" sorgusu her zaman kullanicı + en yeni once
-- siralamasiyla calisacak (bkz. ileride GET /api/notifications/me).
CREATE INDEX idx_in_app_notifications_recipient
    ON in_app_notifications (recipient_user_id, created_at DESC);
