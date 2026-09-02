package com.randevu.backend.service;

import com.randevu.backend.entity.Business;
import com.randevu.backend.repository.BusinessRepository;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

// Faz 2.8: konuma göre yakın işletme listeleme. Bounding box ön filtresi
// + Haversine mesafe (bkz. CLAUDE.md karar tablosu) -- PostGIS DEĞİL:
// ucuz/yönetilen Postgres'lerde eklenti kurulumu ve bakım yükü getirirdi,
// bu ölçekte (yüzlerce işletme) bounding box + Haversine milisaniyeler
// sürer.
//
// ROADMAP'te "native query" öneriliyordu, ama gerçek mesafe hesabı
// (Haversine) burada BİLEREK Java tarafında yapılıyor, ham SQL formülü
// olarak DEĞİL: (1) saf bir fonksiyon olarak birim testi yazmak çok daha
// kolay, (2) Postgres'e özel SQL fonksiyonlarına bağımlı kalmıyor. Bounding
// box ön filtresi (ucuz, adayları daraltan kısım) yine de veritabanında --
// tüm işletmeleri Java'ya çekip filtrelemek ölçek büyüdükçe sorun olurdu.
@Service
public class LocationService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    // 1 derece enlem, dünya üzerinde HER YERDE ~111 km'ye karşılık gelir.
    // Boylam için bu sabit değil -- enleme göre daralır (kutuplara
    // yaklaştıkça meridyenler birbirine yaklaşır), bu yüzden boundingBox'ta
    // cos(lat) ile düzeltiliyor.
    private static final double KM_PER_DEGREE_LATITUDE = 111.0;

    private final BusinessRepository businessRepository;

    public LocationService(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    public record NearbyBusiness(Business business, double distanceKm) {
    }

    // Verilen merkeze radiusKm içindeki işletmeleri, mesafeye göre artan
    // sırada döner. Konumu hiç girilmemiş (latitude/longitude null)
    // işletmeler otomatik dışarıda kalır -- bounding box sorgusu SQL
    // BETWEEN ile çalışıyor, NULL bir değer BETWEEN'e asla eşleşmez.
    public List<NearbyBusiness> findNearby(double centerLat, double centerLng, double radiusKm) {
        BoundingBox box = boundingBox(centerLat, centerLng, radiusKm);

        // Faz 3.9: askidaki isletmeler konum aramasinda da gorunmemeli.
        List<Business> candidates = businessRepository.findByLatitudeBetweenAndLongitudeBetweenAndSuspendedAtIsNull(
                box.minLat(), box.maxLat(), box.minLng(), box.maxLng());

        return candidates.stream()
                .map(b -> new NearbyBusiness(b, haversineKm(centerLat, centerLng, b.getLatitude(), b.getLongitude())))
                // Bounding box bir KARE, aradığımız ise bir DAİRE -- karenin
                // köşelerinde, kutunun içinde ama dairenin (gerçek radiusKm)
                // dışında kalan noktalar olabilir. Bu son, kesin filtre onları eler.
                .filter(nb -> nb.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(NearbyBusiness::distanceKm))
                .toList();
    }

    private record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {
    }

    private BoundingBox boundingBox(double centerLat, double centerLng, double radiusKm) {
        double latDelta = radiusKm / KM_PER_DEGREE_LATITUDE;
        double lngDelta = radiusKm / (KM_PER_DEGREE_LATITUDE * Math.cos(Math.toRadians(centerLat)));
        return new BoundingBox(centerLat - latDelta, centerLat + latDelta,
                centerLng - lngDelta, centerLng + lngDelta);
    }

    // İki koordinat arasındaki büyük daire (great-circle) mesafesini km
    // cinsinden hesaplar. Dünyayı kutuplarda hafifçe basık değil TAM küre
    // varsayıyor -- şehir içi/il içi mesafelerde (bu uygulamanın kapsamı)
    // hata payı gözle görülür değil (metre altı), coğrafi kutuplara yakın
    // ya da kıtalar arası mesafelerde önemli hale gelirdi.
    static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
}
