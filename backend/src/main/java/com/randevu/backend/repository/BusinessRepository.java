package com.randevu.backend.repository;

import com.randevu.backend.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {
    // işletmecinin iş yerlerini getir
    List<Business> findByOwnerId(Long ownerId);
}
