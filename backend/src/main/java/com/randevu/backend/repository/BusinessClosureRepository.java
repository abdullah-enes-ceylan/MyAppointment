package com.randevu.backend.repository;

import com.randevu.backend.entity.BusinessClosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessClosureRepository extends JpaRepository<BusinessClosure, Long> {
    List<BusinessClosure> findByBusinessId(Long businessId);

    // AvailabilityCalculator'dan önce, "bu tarih özel olarak kapatılmış mı?"
    // diye bakmak için kullanılıyor.
    Optional<BusinessClosure> findByBusinessIdAndDate(Long businessId, LocalDate date);
}
