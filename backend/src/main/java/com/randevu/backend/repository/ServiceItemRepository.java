package com.randevu.backend.repository;

import com.randevu.backend.entity.ServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceItemRepository extends JpaRepository<ServiceItem, Long> {
    // Bir işletmeye ait hizmetleri getir
    List<ServiceItem> findByBusinessId(Long businessId);

    // Bir işletmeye ait, silinmemiş (aktif) hizmetleri getirir. Müşteriye
    // randevu için hizmet listesi gösterilirken bu kullanılmalı — soft
    // delete ile "silinmiş" bir hizmet, bu sorgudan otomatik düşer.
    List<ServiceItem> findByBusinessIdAndIsActiveTrue(Long businessId);
}
