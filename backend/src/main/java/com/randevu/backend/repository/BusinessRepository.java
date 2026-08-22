package com.randevu.backend.repository;

import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.BusinessCategory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {
    // işletmecinin iş yerlerini getir
    List<Business> findByOwnerId(Long ownerId);

    List<Business> findByCategory(BusinessCategory category);

    // Faz 2.8: konum bazlı aramanın ucuz ön filtresi (bounding box).
    // Gerçek (dairesel) mesafe hesabı ve kesin filtreleme LocationService'te
    // Java tarafında yapılıyor -- bu sorgu sadece adayları daraltıyor, kutu
    // köşelerinde dairenin dışında kalan noktalar da dönebilir (bilerek,
    // bkz. LocationService).
    List<Business> findByLatitudeBetweenAndLongitudeBetween(
            Double minLatitude, Double maxLatitude, Double minLongitude, Double maxLongitude);
}
