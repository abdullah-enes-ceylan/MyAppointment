package com.randevu.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// "Sadece gerçekten gitmiş kişi yorum yapabilir" garantisinin ŞEMA
// katmanı -- üç katmanlı savunmanın en altı ve asıl garantisi (bkz.
// ROADMAP 2.6). Review bilerek Business'e değil APPOINTMENT'a bağlanıyor
// ve appointment_id UNIQUE: bir yorum belirli bir GERÇEKLEŞMİŞ randevuya
// demirleniyor. Bu sayede "randevusu olmayan yorum yazamaz" ve "bir
// randevuya iki yorum yazılamaz" kuralları, servis katmanındaki kontroller
// bir gün bozulsa/atlansa bile veritabanının REDDETTİĞİ bir şey olur.
// Servis katmanındaki ek kontroller (customer eşleşmesi, status==COMPLETED,
// zaman penceresi) bkz. ReviewService.
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @Column(nullable = false)
    private int rating;

    // Nullable: musteri sadece puan verip yorum yazmayabilir.
    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
