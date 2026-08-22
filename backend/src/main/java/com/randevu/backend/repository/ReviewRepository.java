package com.randevu.backend.repository;

import com.randevu.backend.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    // "Bu randevuya zaten yorum yapılmış mı?" -- appointment_id UNIQUE
    // kısıtının (V6) servis katmanındaki karşılığı, DB'ye gitmeden önce
    // kullanıcıya anlamlı bir hata mesajı verebilmek için.
    Optional<Review> findByAppointmentId(Long appointmentId);

    // Bir işletmenin aldığı tüm yorumlar -- Appointment üzerinden JOIN.
    // Business'e doğrudan FK yok (bkz. Review entity'sindeki açıklama),
    // bu yüzden Spring Data'nın iç içe özellik (nested property) sözdizimi
    // kullanılıyor: Appointment.business.id.
    List<Review> findByAppointment_Business_IdOrderByCreatedAtDesc(Long businessId);
}
