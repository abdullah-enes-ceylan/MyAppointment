package com.randevu.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

// Bir isletmenin personeli (ornegin berber dukkanindaki bir kuafor).
// "Ayni saate 2 kisi alabilmeliyim" ihtiyacinin cozumu budur -- Faz 2.4'te
// AvailabilityCalculator personel bazli calisacak, Faz 2.5'te Appointment'a
// nullable staff_id eklenecek. Bu adimda (2.3) sadece veri modeli ve CRUD var,
// randevu akisina henuz baglanmadi.
@Entity
@Table(name = "staff")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(nullable = false)
    private String name;

    // ServiceItem'daki soft-delete deseniyle ayni sebep: Faz 2.5'te
    // Appointment.staff eklendiginde, gecmis randevular ayrilmis bir
    // personele hala FK ile referans veriyor olacak -- gercek DELETE bu
    // referansi kirar. Isten ayrilan personel isActive=false yapilir,
    // yeni randevu icin secilemez ama gecmis kayitlar saglam kalir.
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    // Bu personelin verebildigi hizmetler. ManyToMany: bir personel birden
    // fazla hizmet verebilir (ornegin hem sac kesimi hem sakal tirasi), bir
    // hizmet birden fazla personel tarafindan verilebilir. FetchType.EAGER
    // DEGIL (varsayilan LAZY) -- StaffResponse'a cevrilirken servis
    // katmaninda ihtiyac oldukca erisiliyor, her Staff sorgusunda otomatik
    // JOIN yapmaya gerek yok.
    @ManyToMany
    @JoinTable(
            name = "staff_services",
            joinColumns = @JoinColumn(name = "staff_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id"))
    @Builder.Default
    private Set<ServiceItem> services = new HashSet<>();
}
