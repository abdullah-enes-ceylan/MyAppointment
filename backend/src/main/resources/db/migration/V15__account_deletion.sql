-- Hesap silme akisi (KVKK unutulma hakki, Faz 3.9). Ucu nullable -- mevcut
-- satirlar icin varsayilan "silme talep edilmemis" anlamina geliyor,
-- expand-contract kuralina uygun (bkz. CLAUDE.md karar tablosu).

ALTER TABLE users ADD COLUMN deletion_requested_at TIMESTAMP;
ALTER TABLE users ADD COLUMN anonymized_at TIMESTAMP;

ALTER TABLE businesses ADD COLUMN suspended_at TIMESTAMP;
