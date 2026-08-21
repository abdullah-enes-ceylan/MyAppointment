package com.randevu.backend.repository;

import com.randevu.backend.entity.WorkingHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkingHourRepository extends JpaRepository<WorkingHour, Long> {
    List<WorkingHour> findByBusinessId(Long businessId);

    // AvailabilityCalculator'a hangi saatlerin kullanılacağını belirlerken
    // kullanılıyor — "bu işletme bu haftanın günü için özel saat girmiş mi?"
    Optional<WorkingHour> findByBusinessIdAndDayOfWeek(Long businessId, DayOfWeek dayOfWeek);
}
