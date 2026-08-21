package com.randevu.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

// Bir işletmenin, haftanın BELİRLİ bir günü için çalışma saatlerini taşır.
// Business başına en fazla 7 satır olur (her gün için bir tane). Eskiden
// Business.openTime/closeTime tek bir çift olarak tüm haftaya uygulanıyordu
// — kapalı gün, farklı hafta sonu saati, öğle molası hiç modellenemiyordu.
// dayOfWeek için java.time.DayOfWeek doğrudan kullanılıyor — ayrı bir enum
// yazmaya gerek yok, JPA @Enumerated(STRING) ile sorunsuz çalışıyor.
//
// Not: Bu entity hiçbir zaman doğrudan @RequestBody olarak kullanılmıyor
// (WorkingHourRequest/Response DTO'ları üzerinden gidiliyor) — bu yüzden
// ServiceItem'daki gibi Jackson @JsonCreator/@JsonIgnore iş-aroundlarına
// burada gerek yok, o sorun hiç oluşmuyor.
@Entity
@Table(name = "working_hours")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkingHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    // isClosed=true ise null olabilir (o gün hiç açılmıyor demektir).
    private LocalTime openTime;
    private LocalTime closeTime;

    @Builder.Default
    @Column(name = "is_closed", nullable = false)
    private boolean isClosed = false;
}
