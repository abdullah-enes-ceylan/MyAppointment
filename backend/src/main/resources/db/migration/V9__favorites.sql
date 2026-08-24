-- ============================================================
--  V9 — Favoriler. Kullanıcı-işletme ilişkisini ayrı bir tabloda
--  tutuyoruz (User/Business üzerinde bir liste alanı değil) --
--  Review'daki aynı gerekçe: tekilleştirme (bir kullanıcı aynı
--  işletmeyi iki kez favoriye ekleyemesin) DB seviyesinde UNIQUE
--  constraint ile garanti altına alınıyor, uygulama kodu bir gün
--  bu kontrolü atlasa bile veritabanı reddediyor.
-- ============================================================

CREATE TABLE favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    business_id BIGINT NOT NULL REFERENCES businesses(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ux_favorites_user_business UNIQUE (user_id, business_id)
);

CREATE INDEX idx_favorites_user ON favorites (user_id);
