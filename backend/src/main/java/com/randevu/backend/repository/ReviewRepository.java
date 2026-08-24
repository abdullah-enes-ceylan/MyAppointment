package com.randevu.backend.repository;

import com.randevu.backend.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Faz 2.7: puan ortalaması ve yorum sayısı -- önce basit aggregate
    // sorgu (bkz. ROADMAP 2.7). Denormalize bir Business.ratingAverage
    // alanı YOK bilerek: 5-10 işletmelik beta ölçeğinde bu sorgu zaten
    // milisaniyeler sürer, denormalizasyon (her yorum eklendiğinde
    // Business satırını da güncelleme, tutarlılık riski) şimdiden
    // gerekli değil -- erken optimizasyon yapma.
    @Query("SELECT AVG(r.rating) as averageRating, COUNT(r) as reviewCount "
            + "FROM Review r WHERE r.appointment.business.id = :businessId")
    ReviewStatsProjection getStatsForBusiness(@Param("businessId") Long businessId);

    // Profil ekranindaki "yaptigim yorum sayisi". Yukaridaki isletme bazli
    // sorgunun aynasi: orada Appointment.business, burada Appointment.customer
    // uzerinden -- Review'in Business'e de User'a da dogrudan FK'si yok, ikisi
    // de Appointment uzerinden JOIN'leniyor (bkz. Review entity'sindeki aciklama).
    long countByAppointment_Customer_Id(Long customerId);
}
