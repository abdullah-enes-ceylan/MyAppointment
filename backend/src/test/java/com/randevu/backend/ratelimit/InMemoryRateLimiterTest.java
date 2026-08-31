package com.randevu.backend.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

// Spring baglami YOK -- InMemoryRateLimiter bilerek JPA/Spring'den bagimsiz
// (AvailabilityCalculatorTest/AppointmentExpiryPolicyTest ile ayni desen).
// Clock.fixed() TEK bir ani dondurdugu icin pencere GECISLERINI (zaman
// ilerledikce ayni anahtarin durumunun degismesi) test edemez -- bu yuzden
// burada "saati elle ilerletilebilen" kucuk bir test Clock'u kullaniliyor.
// Bu, Clock disiplinini BOZMUYOR: InMemoryRateLimiter hala enjekte edilen
// Clock'tan okuyor, sadece test bu Clock'un hangi ani dondurdugunu kontrol
// ediyor.
class InMemoryRateLimiterTest {

    private MutableClock clock;
    private InMemoryRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-01T10:00:00Z"));
        rateLimiter = new InMemoryRateLimiter(clock);
    }

    // --- tryConsume (hacim siniri) ---

    @Test
    @DisplayName("tryConsume: sinira kadar olan istekler kabul edilir, sinir asilinca reddedilir")
    void tryConsume_siniriAsanIstekReddedilir() {
        String key = "ip:1.2.3.4:register";
        Duration window = Duration.ofMinutes(15);

        for (int i = 1; i <= 3; i++) {
            assertThat(rateLimiter.tryConsume(key, 3, window).allowed()).isTrue();
        }

        RateLimitResult fourth = rateLimiter.tryConsume(key, 3, window);
        assertThat(fourth.allowed()).isFalse();
        assertThat(fourth.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    @DisplayName("tryConsume: pencere TAM SINIRINDA sifirlanir (>=), bir milisaniye once sifirlanmaz")
    void tryConsume_pencereSiniriTamZamaninda() {
        String key = "ip:1.2.3.4:available-slots";
        Duration window = Duration.ofMinutes(1);

        // Pencereyi doldur.
        rateLimiter.tryConsume(key, 1, window); // 1. istek -- kabul
        assertThat(rateLimiter.tryConsume(key, 1, window).allowed()).isFalse(); // 2. istek -- red

        // Pencere bitmeden 1 ms once: hala AYNI pencere, hala reddedilir.
        clock.advanceTo(clock.instant().plusMillis(window.toMillis() - 1));
        assertThat(rateLimiter.tryConsume(key, 1, window).allowed()).isFalse();

        // Tam pencere suresi gectiginde (>=): YENI pencere baslar, kabul edilir.
        clock.advanceTo(clock.instant().plusMillis(1)); // artik tam olarak window kadar ilerledi
        assertThat(rateLimiter.tryConsume(key, 1, window).allowed()).isTrue();
    }

    // --- checkBlocked / recordFailure / reset (login kaba-kuvvet deseni) ---

    @Test
    @DisplayName("checkBlocked: son basarisiz denemeden pencere kadar once/sonra sinirda dogru davranir")
    void checkBlocked_pencereSiniriTamZamaninda() {
        String key = "login:account:test@example.com";
        Duration window = Duration.ofMinutes(15);
        int maxAttempts = 3;

        for (int i = 1; i <= maxAttempts; i++) {
            rateLimiter.recordFailure(key, window);
        }
        // Tam limitte: blokeli.
        assertThat(rateLimiter.checkBlocked(key, maxAttempts, window).allowed()).isFalse();

        // Pencere bitmeden 1 ms once: hala blokeli.
        clock.advanceTo(clock.instant().plusMillis(window.toMillis() - 1));
        assertThat(rateLimiter.checkBlocked(key, maxAttempts, window).allowed()).isFalse();

        // Tam pencere suresi gectiginde (>=): blokaj kalkar.
        clock.advanceTo(clock.instant().plusMillis(1));
        assertThat(rateLimiter.checkBlocked(key, maxAttempts, window).allowed()).isTrue();
    }

    @Test
    @DisplayName("reset: basarili giristen sonra sayaç sifirlanir, hemen ardindan blokeli degildir")
    void reset_sayaciTemizler() {
        String key = "login:account:test@example.com";
        Duration window = Duration.ofMinutes(15);
        int maxAttempts = 3;

        for (int i = 1; i <= maxAttempts; i++) {
            rateLimiter.recordFailure(key, window);
        }
        assertThat(rateLimiter.checkBlocked(key, maxAttempts, window).allowed()).isFalse();

        rateLimiter.reset(key);

        assertThat(rateLimiter.checkBlocked(key, maxAttempts, window).allowed()).isTrue();
    }

    // --- Anahtarlarin bagimsizligi ---

    @Test
    @DisplayName("Farkli anahtarlar birbirinden tamamen bagimsizdir")
    void farkliAnahtarlarBirbirindenBagimsiz() {
        Duration window = Duration.ofMinutes(15);

        rateLimiter.recordFailure("login:account:victim@example.com", window);
        rateLimiter.recordFailure("login:account:victim@example.com", window);
        rateLimiter.recordFailure("login:account:victim@example.com", window);

        // Ayni pencerede BASKA bir anahtar (farkli hesap) hic etkilenmemis olmali.
        assertThat(rateLimiter.checkBlocked("login:account:victim@example.com", 3, window).allowed()).isFalse();
        assertThat(rateLimiter.checkBlocked("login:account:other@example.com", 3, window).allowed()).isTrue();
    }

    // Clock.fixed()'in aksine, saati elle ilerletmeye izin veren kucuk bir
    // test yardimcisi. Sadece testlerde kullanilir, uretim kodunda hicbir
    // yerde referans verilmez.
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceTo(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("Test yardimcisi -- zone degistirmeye gerek yok");
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
