package com.randevu.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

// WorkingHour'un personel bazli esdegeri -- ayni tasarim, farkli sahiplik
// (Business yerine Staff). Ayri bir entity olmasinin sebebi WorkingHour'daki
// ile ayni: personel calisma saatleri Faz 2.4'te AvailabilityCalculator'a
// baglanacak, o zamana kadar bu sadece veri modeli. Bir Staff satiri
// baslangicta hic StaffWorkingHour girmemis olabilir -- bu durumda Faz 2.4
// muhtemelen isletmenin kendi WorkingHour'una duser (WorkingHour'un
// Business.openTime/closeTime'a dustugu gibi), o karar o adimda verilecek.
@Entity
@Table(name = "staff_working_hours")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffWorkingHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    // isClosed=true ise null olabilir (o gun hic calismiyor demektir).
    private LocalTime openTime;
    private LocalTime closeTime;

    @Builder.Default
    @Column(name = "is_closed", nullable = false)
    private boolean isClosed = false;
}
