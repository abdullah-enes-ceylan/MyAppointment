package com.randevu.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

// Uygulamadaki TEK zaman kaynagi.
//
// Neden gerekli: kodda 10 ayri yerde LocalDateTime.now() / Instant.now()
// cagriliyordu ve hicbiri saat dilimini acikca belirtmiyordu -- yani hepsi
// JVM'in (dolayisiyla isletim sisteminin) varsayilanina bagliydi. Bu su an
// tesadufen calisiyor cunku gelistirme makinesi de Postgres de
// Europe/Istanbul. Uretimde (sunucu UTC, konteyner baska bir TZ) ayni kod
// saatlerce kaymis degerler uretebilirdi.
//
// Sema tarafi bu riski BUYUTUYOR: butun zaman kolonlari "timestamp without
// time zone", yani veritabani hicbir donusum yapmiyor, sakladigi sey ciplak
// duvar saati. Bu degerin hangi saat dilimini ifade ettigine dair tek
// otorite uygulamanin kendisi. O yuzden saat dilimini sabitlemek "iyi olur"
// degil, bu semada tek koruma.
//
// Clock'un bean olmasinin ikinci faydasi: testlerde Clock.fixed(...) ile
// zaman dondurulabiliyor. LocalDateTime.now() dogrudan cagrildiginda
// "gece yarisi civarinda ne oluyor" gibi durumlari test etmek mumkun degildi.
@Configuration
public class TimeConfig {

    private static final Logger log = LoggerFactory.getLogger(TimeConfig.class);

    // CLAUDE.md karar tablosu: zaman LocalDateTime + Europe/Istanbul.
    // Ayardan okunuyor ki test/farkli ortamlarda degistirilebilsin.
    @Value("${app.time-zone:Europe/Istanbul}")
    private String zoneId;

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of(zoneId));
    }

    // JVM varsayilanini da ayni bolgeye sabitliyoruz. Clock bean'i zaten
    // enjekte edildigi her yerde dogru sonucu veriyor; bu ek adim, Clock
    // kullanmayan kod yollari icin (kutuphaneler, loglama, ileride
    // eklenecek bir yerde unutulan bir now() cagrisi) ayni sonucu garanti
    // ediyor. Global bir mutasyon oldugu icin acikca loglaniyor -- ileride
    // bir tarih/saat sorunu arastirilirken bu satirin gorunur olmasi onemli.
    @PostConstruct
    public void pinDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        log.info("Uygulama saat dilimi sabitlendi: {}", zoneId);
    }
}
