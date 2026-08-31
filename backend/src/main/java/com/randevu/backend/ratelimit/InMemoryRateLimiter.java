package com.randevu.backend.ratelimit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// Faz 3.5: elle yazilmis, bagimliliksiz sabit-pencere (fixed-window) sayac.
// Bucket4j gibi bir kutuphane yerine bilerek elle yazildi -- bu projenin cok
// yeni Spring Boot surumunde her yeni bagimlilik (testcontainers-bom,
// springdoc-openapi) bir surum uyumsuzlugu arastirmasi gerektirdi; bu
// olcekte (tek sunucu, beta) bir sabit pencere sayaci dogru yazmak, yeni bir
// kutuphanenin surum riskini almaktan daha ucuz. Spring'in kendisi de bunun
// icin yerlesik bir cozum sunmuyor (Spring Cloud Gateway'in RequestRateLimiter'i
// var ama ayri, agir bir bagimlilik, bu projede kullanilmiyor).
//
// Tek node icin yeterli -- coklu instance'a gecilirse (ROADMAP'te henuz plan
// yok) bu RateLimitPort'un YENI bir implementasyonu (Redis tabanli) yazilip
// bean olarak degistirilmeli, aksi halde her instance kendi sayacini tutar
// ve gercek sinir instance sayisiyla carpilmis olur.
@Component
public class InMemoryRateLimiter implements RateLimitPort {

    // Pencere baslangicindan BU KADAR eski kayitlar temizlenir (bkz.
    // evictExpired). Herhangi bir iş kurali degil, sadece bellek hijyeni --
    // projedeki en uzun pencere (loginAccountWindow/loginIpWindow/registerWindow,
    // varsayilan 15 dk) bile bunun cok altinda kaliyor.
    private static final Duration EVICTION_RETENTION = Duration.ofHours(1);

    private final Clock clock;
    private final ConcurrentHashMap<String, WindowState> windows = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public RateLimitResult tryConsume(String key, int maxRequests, Duration window) {
        WindowState state = windows.compute(key, (k, existing) -> incrementOrReset(existing, window));
        if (state.count() <= maxRequests) {
            return new RateLimitResult(true, 0);
        }
        return new RateLimitResult(false, secondsUntilReset(state, window));
    }

    @Override
    public RateLimitResult checkBlocked(String key, int maxAttempts, Duration window) {
        WindowState state = windows.get(key);
        if (state == null || isExpired(state, window)) {
            return new RateLimitResult(true, 0);
        }
        if (state.count() < maxAttempts) {
            return new RateLimitResult(true, 0);
        }
        return new RateLimitResult(false, secondsUntilReset(state, window));
    }

    @Override
    public void recordFailure(String key, Duration window) {
        windows.compute(key, (k, existing) -> incrementOrReset(existing, window));
    }

    @Override
    public void reset(String key) {
        windows.remove(key);
    }

    // compute() atomik calisir -- ayni anahtara es zamanli gelen iki istek
    // icin klasik "check-then-act" yarisi (ikisi de "hala sinirin altinda"
    // gorup ikisinin de gecmesi) mumkun degil.
    private WindowState incrementOrReset(WindowState existing, Duration window) {
        long now = clock.millis();
        if (existing == null || isExpired(existing, window)) {
            return new WindowState(now, 1);
        }
        return new WindowState(existing.windowStartMillis(), existing.count() + 1);
    }

    private boolean isExpired(WindowState state, Duration window) {
        return clock.millis() - state.windowStartMillis() >= window.toMillis();
    }

    private long secondsUntilReset(WindowState state, Duration window) {
        long elapsedMillis = clock.millis() - state.windowStartMillis();
        long remainingMillis = Math.max(0, window.toMillis() - elapsedMillis);
        // Yukari yuvarla: 1ms bile kalsa istemciye "0 saniye sonra tekrar
        // dene" denmemeli.
        return (remainingMillis + 999) / 1000;
    }

    // Harita hicbir zaman kucultulmezse her yeni IP/e-posta/kullanici id
    // sonsuza kadar bellekte kalir -- bu, dusuk trafikli bir beta icin bile
    // yavas bir bellek sizintisi olurdu. Yarim saatte bir, EVICTION_RETENTION'dan
    // eski (yani zaten uzun suredir hicbir pencerenin gecerli sayilamayacagi)
    // kayitlar silinir.
    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES)
    public void evictExpired() {
        long cutoff = clock.millis() - EVICTION_RETENTION.toMillis();
        windows.entrySet().removeIf(entry -> entry.getValue().windowStartMillis() < cutoff);
    }

    private record WindowState(long windowStartMillis, int count) {
    }
}
