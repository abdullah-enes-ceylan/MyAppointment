package com.randevu.backend.ratelimit;

import java.time.Duration;

// Faz 3.5: BusinessPhotoStorage/NotificationPort ile ayni desen -- bugun
// tek implementasyon bellek ici (InMemoryRateLimiter), ileride birden fazla
// instance calistirilirsa Redis tabanli bir implementasyon tek yeni sinif +
// bean degisikligi olur, bu arayuzu cagiran hicbir kod (AuthController,
// RateLimitFilter, BusinessController) degismez.
public interface RateLimitPort {

    // HACIM siniri: her cagri hem SAYAR hem karar verir (register,
    // available-slots, fotograf yukleme, genel guvenlik agi).
    RateLimitResult tryConsume(String key, int maxRequests, Duration window);

    // SAYMADAN sadece "su an blokeli mi" sorusuna cevap verir (login kaba-
    // kuvvet korumasi bunu kullanir, sayma islemi ayri bir metotta cunku
    // sadece BASARISIZ denemeler sayilmali).
    RateLimitResult checkBlocked(String key, int maxAttempts, Duration window);

    // Basarisiz bir denemeyi sayar.
    void recordFailure(String key, Duration window);

    // Basarili bir denemeden sonra sayaci temizler.
    void reset(String key);
}
