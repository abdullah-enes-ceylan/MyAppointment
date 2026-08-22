package com.randevu.backend.repository;

import com.randevu.backend.entity.StaffWorkingHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Repository
public interface StaffWorkingHourRepository extends JpaRepository<StaffWorkingHour, Long> {
    List<StaffWorkingHour> findByStaffId(Long staffId);

    Optional<StaffWorkingHour> findByStaffIdAndDayOfWeek(Long staffId, DayOfWeek dayOfWeek);
}
