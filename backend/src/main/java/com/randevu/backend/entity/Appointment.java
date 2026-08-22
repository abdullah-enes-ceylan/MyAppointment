package com.randevu.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime appointmentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceItem serviceItem;

    // Faz 2.5: nullable -- personel sistemi kullanmayan (henuz hic Staff
    // eklememis) bir isletmenin randevulari icin null kalir, cakisma kontrolu
    // business_id+tarih uzerinden yapilmaya devam eder (mevcut/eski davranis,
    // geriye donuk tam uyumlu). Personel atanmis bir randevuda cakisma
    // kontrolu staff_id+tarih uzerinden yapilir -- boylece ayni saatte farkli
    // personellere randevu alinabilir ("ayni saate 2 kisi" ihtiyaci).
    // bkz. AppointmentService.createAppointment ve V5 migration'daki iki
    // ayri partial unique index.
    @ManyToOne
    @JoinColumn(name = "staff_id", nullable = true)
    private Staff staff;

}
