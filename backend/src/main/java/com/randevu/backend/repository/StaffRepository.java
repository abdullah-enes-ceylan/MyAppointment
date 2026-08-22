package com.randevu.backend.repository;

import com.randevu.backend.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {
    // Isten ayrilmis (isActive=false) personel otomatik disarida kalir --
    // ne musteri secim listesinde ne de yeni randevu atamasinda gorunur.
    List<Staff> findByBusinessIdAndIsActiveTrue(Long businessId);
}
