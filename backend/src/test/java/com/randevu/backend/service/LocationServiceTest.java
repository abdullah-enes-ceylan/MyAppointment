package com.randevu.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// haversineKm paket-özel (package-private) static bir metot -- Spring
// context'e ihtiyaç yok, saf matematik. Bounding box + gerçek DB sorgusu
// (findNearby) buradan test edilmiyor çünkü BusinessRepository gerektirir
// -- o kısım Faz 3.1'in entegrasyon testlerinin kapsamına daha uygun.
class LocationServiceTest {

    // Kadıköy (Bağdat Cd.) - Beşiktaş arası kabaca 8-9 km, bilinen bir
    // referans mesafe ile karşılaştırmak (elle hesaplanmış bir sabitle
    // değil) gerçek dünya sağlaması için kullanıldı.
    private static final double KADIKOY_LAT = 40.9800;
    private static final double KADIKOY_LNG = 29.0280;
    private static final double BESIKTAS_LAT = 41.0430;
    private static final double BESIKTAS_LNG = 29.0090;

    @Test
    void ayniNokta_mesafeSifirdir() {
        double distance = LocationService.haversineKm(41.0, 29.0, 41.0, 29.0);
        assertThat(distance).isCloseTo(0.0, within(0.0001));
    }

    @Test
    void bilinenIkiNokta_makulMesafeDoner() {
        double distance = LocationService.haversineKm(KADIKOY_LAT, KADIKOY_LNG, BESIKTAS_LAT, BESIKTAS_LNG);
        // Kesin km yerine makul bir aralik: kus ucusu ~7-9 km.
        assertThat(distance).isBetween(6.0, 10.0);
    }

    @Test
    void mesafeSimetriktir() {
        double ab = LocationService.haversineKm(KADIKOY_LAT, KADIKOY_LNG, BESIKTAS_LAT, BESIKTAS_LNG);
        double ba = LocationService.haversineKm(BESIKTAS_LAT, BESIKTAS_LNG, KADIKOY_LAT, KADIKOY_LNG);
        assertThat(ab).isCloseTo(ba, within(0.0001));
    }

    @Test
    void ekvatordaBirDerecelikBoylamFarki_yaklasik111KmDir() {
        // Ekvatorda (lat=0) boylam daralması yok, 1 derece boylam ~ 1
        // derece enlem ~ 111 km'ye esit olmali.
        double distance = LocationService.haversineKm(0, 0, 0, 1);
        assertThat(distance).isCloseTo(111.32, within(1.0));
    }
}
