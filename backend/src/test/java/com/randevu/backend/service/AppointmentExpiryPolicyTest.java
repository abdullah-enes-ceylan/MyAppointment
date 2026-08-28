package com.randevu.backend.service;

import com.randevu.backend.config.AppointmentPolicyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Spring baglami YOK: policy saf bir sinif, ayarlari da elle kuruyoruz.
// Testte gecen "now" degerlerinin hicbiri gercek saatten gelmiyor --
// ya sabit literal ya Clock.fixed(). Gercek saat teste karismiyor, yani
// bu testler gecenin hangi saatinde calistirilirsa calistirilsin ayni
// sonucu veriyor.
class AppointmentExpiryPolicyTest {

    private AppointmentExpiryPolicy policy;

    @BeforeEach
    void setUp() {
        AppointmentPolicyProperties props = new AppointmentPolicyProperties();
        props.validate(); // varsayilanlar: 1 saat, %10, 90 gun, 3
        policy = new AppointmentExpiryPolicy(props);
    }

    @Test
    @DisplayName("Uzun pencerede sabit pay uygulanir (randevudan 1 saat once)")
    void longWindowUsesFixedLeadTime() {
        LocalDateTime alindi = LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime randevu = LocalDateTime.of(2026, 8, 25, 10, 0); // 5 gun

        assertEquals(LocalDateTime.of(2026, 8, 25, 9, 0), policy.expiresAt(alindi, randevu));
    }

    @Test
    @DisplayName("Kisa pencerede oran uygulanir (pencerenin %10'u)")
    void shortWindowUsesRatio() {
        LocalDateTime alindi = LocalDateTime.of(2026, 8, 25, 4, 0);
        LocalDateTime randevu = LocalDateTime.of(2026, 8, 25, 10, 0); // 6 saat -> %10 = 36 dk

        assertEquals(LocalDateTime.of(2026, 8, 25, 9, 24), policy.expiresAt(alindi, randevu));
    }

    @Test
    @DisplayName("Cok kisa pencerede talep dogdugu anda dusmez")
    void veryShortWindowStillLeavesTime() {
        LocalDateTime alindi = LocalDateTime.of(2026, 8, 25, 9, 30);
        LocalDateTime randevu = LocalDateTime.of(2026, 8, 25, 10, 0); // 30 dk -> %10 = 3 dk

        LocalDateTime duser = policy.expiresAt(alindi, randevu);

        assertEquals(LocalDateTime.of(2026, 8, 25, 9, 57), duser);
        // Asil mesele: sabit pay kullanilsaydi bu talep ALINDIGI ANDAN
        // once duserdi. Dusme ani talep aninin ILERISINDE olmali.
        assertTrue(duser.isAfter(alindi), "talep dogdugu anda dusmemeli");
    }

    @Test
    @DisplayName("Gecis noktasi: 10 saatlik pencerede iki kural esitlenir")
    void crossoverPoint() {
        LocalDateTime alindi = LocalDateTime.of(2026, 8, 25, 0, 0);
        LocalDateTime randevu = LocalDateTime.of(2026, 8, 25, 10, 0); // %10 = 1 saat = sabit pay

        assertEquals(LocalDateTime.of(2026, 8, 25, 9, 0), policy.expiresAt(alindi, randevu));
    }

    @Test
    @DisplayName("Randevu saati talep aninda/oncesindeyse derhal duser")
    void nonPositiveWindowExpiresImmediately() {
        LocalDateTime randevu = LocalDateTime.of(2026, 8, 25, 10, 0);
        LocalDateTime alindi = LocalDateTime.of(2026, 8, 26, 10, 0); // randevudan SONRA

        assertEquals(randevu, policy.expiresAt(alindi, randevu));
        assertTrue(policy.isExpired(alindi, randevu, randevu));
    }

    // Gun sinirini asan pencere -- TZ/tarih islerinde en sik kacirilan yer.
    @Test
    @DisplayName("Gece yarisini asan pencerede hesap kaymaz")
    void windowSpanningMidnight() {
        LocalDateTime alindi = LocalDateTime.of(2026, 8, 25, 23, 50);
        LocalDateTime randevu = LocalDateTime.of(2026, 8, 26, 0, 20); // 30 dk, gun degisiyor

        assertEquals(LocalDateTime.of(2026, 8, 26, 0, 17), policy.expiresAt(alindi, randevu));
    }

    @Test
    @DisplayName("isExpired sinirda: tam dusme aninda dusmus sayilir")
    void isExpiredAtBoundary() {
        LocalDateTime alindi = LocalDateTime.of(2026, 8, 20, 10, 0);
        LocalDateTime randevu = LocalDateTime.of(2026, 8, 25, 10, 0);
        LocalDateTime duser = LocalDateTime.of(2026, 8, 25, 9, 0);

        assertFalse(policy.isExpired(alindi, randevu, duser.minusSeconds(1)));
        assertTrue(policy.isExpired(alindi, randevu, duser));
        assertTrue(policy.isExpired(alindi, randevu, duser.plusSeconds(1)));
    }

    // Clock.fixed() ile: gercek saat testin sonucunu etkilemiyor.
    @Test
    @DisplayName("Sabit Clock ile ufuk kontrolu")
    void horizonWithFixedClock() {
        Clock sabit = Clock.fixed(Instant.parse("2026-08-25T07:00:00Z"), ZoneId.of("Europe/Istanbul"));
        LocalDateTime now = LocalDateTime.now(sabit); // 2026-08-25T10:00

        assertFalse(policy.isBeyondHorizon(now.plusDays(89), now));
        assertTrue(policy.isBeyondHorizon(now.plusDays(91), now));
    }

    @Test
    @DisplayName("Gecersiz ayarlar guvenli varsayilana duser")
    void invalidConfigFallsBackToDefaults() {
        AppointmentPolicyProperties bozuk = new AppointmentPolicyProperties();
        bozuk.setExpiryRatio(5.0);                 // (0,1) disinda
        bozuk.setExpiryLeadTime(Duration.ZERO);    // pozitif olmali
        bozuk.setBookingHorizon(Duration.ofDays(-1));
        bozuk.setMaxOpenRequestsPerBusiness(0);
        bozuk.validate();

        assertEquals(Duration.ofHours(1), bozuk.getExpiryLeadTime());
        assertEquals(0.10, bozuk.getExpiryRatio());
        assertEquals(Duration.ofDays(90), bozuk.getBookingHorizon());
        assertEquals(3, bozuk.getMaxOpenRequestsPerBusiness());
    }
}
