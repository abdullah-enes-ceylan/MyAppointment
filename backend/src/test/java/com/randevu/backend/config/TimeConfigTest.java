package com.randevu.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

// Saat dilimi sabitlemesinin regresyon kalkani.
//
// Bu testler Spring baglami gerektirmiyor: TimeConfig'in urettigi Clock'u
// dogrudan kuruyoruz. Amac, ileride biri app.time-zone ayarini degistirir
// ya da sabitlemeyi kaldirirsa bunun sessizce gecmemesi.
class TimeConfigTest {

    private Clock clockForZone(String zone) {
        TimeConfig config = new TimeConfig();
        ReflectionTestUtils.setField(config, "zoneId", zone);
        return config.clock();
    }

    @Test
    @DisplayName("Clock, yapilandirilan saat dilimini kullanir")
    void clockUsesConfiguredZone() {
        assertEquals(ZoneId.of("Europe/Istanbul"), clockForZone("Europe/Istanbul").getZone());
    }

    // Sabitlemenin NEDEN gerektigini somutlastiran test.
    //
    // Ayni AN (Instant) iki farkli saat diliminde farkli TARIHE dusuyor.
    // Kodda gun sinirlarina duyarli mantik var (gecmis randevularin
    // tamamlanmasi, "yaklasan randevular", 30 gunluk yorum penceresi) ve
    // butun zaman kolonlari veritabaninda saat dilimi TASIMIYOR -- yani
    // yanlis dilimde uretilen bir deger sessizce yanlis gune yazilir,
    // hicbir yerde hata vermez.
    //
    // Secilen an bilerek gece yarisina yakin: 21:30 UTC = ertesi gun
    // 00:30 Istanbul. TZ islerinde en sik kacirilan nokta tam burasi.
    @Test
    @DisplayName("Gece yarisi civarinda saat dilimi TARIHI degistirir")
    void zoneShiftsDateNearMidnight() {
        Instant an = Instant.parse("2026-08-28T21:30:00Z");

        LocalDateTime istanbul = LocalDateTime.ofInstant(an, ZoneId.of("Europe/Istanbul"));
        LocalDateTime utc = LocalDateTime.ofInstant(an, ZoneId.of("UTC"));

        assertEquals(LocalDate.of(2026, 8, 29), istanbul.toLocalDate(), "Istanbul'da ertesi gun");
        assertEquals(LocalDate.of(2026, 8, 28), utc.toLocalDate(), "UTC'de hala ayni gun");
        assertNotEquals(istanbul.toLocalDate(), utc.toLocalDate());
    }

    // Yukaridaki riskin uygulamada kapali oldugunun kaniti: yapilandirilmis
    // Clock ile uretilen deger, gece yarisi civarindaki bir anda dogru
    // (Istanbul) tarihi veriyor.
    @Test
    @DisplayName("Yapilandirilmis Clock, gece yarisi civarinda dogru gunu verir")
    void configuredClockGivesIstanbulDate() {
        Instant an = Instant.parse("2026-08-28T21:30:00Z");
        Clock sabit = Clock.fixed(an, clockForZone("Europe/Istanbul").getZone());

        assertEquals(LocalDate.of(2026, 8, 29), LocalDate.now(sabit));
        assertEquals(LocalDateTime.of(2026, 8, 29, 0, 30), LocalDateTime.now(sabit));
    }
}
