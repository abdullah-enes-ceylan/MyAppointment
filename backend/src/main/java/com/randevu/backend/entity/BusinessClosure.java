package com.randevu.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

// Bir işletmenin BELİRLİ bir günde (tatil, özel kapanış vb.) kapalı olduğunu
// gösterir. WorkingHour'dan farkı: WorkingHour haftalık, TEKRARLANAN kuralı
// tutar ("her Pazar kapalıyız"), BusinessClosure ise haftalık kurala göre
// açık olması gereken bir günün İSTİSNAİ olarak kapalı olduğunu tutar
// ("normalde Salı açığız ama 29 Ekim'de kapalıyız"). Çok günlük bir tatil
// tek bir aralık (range) satırı olarak DEĞİL, her gün için ayrı bir satır
// olarak tutuluyor — sorgu ("bu tarih kapalı mı?") bu şekilde çok daha
// basit kalıyor, aralık çakışma mantığına hiç gerek kalmıyor.
//
// Not: Bilerek "BusinessException" değil "BusinessClosure" adı verildi —
// Java'da *Exception isimleri Throwable'dan türeyen sınıflar için ayrılmış
// bir konvansiyondur, bir JPA entity'sine bu adı vermek kafa karıştırırdı
// (özellikle bu projenin zaten bir exception/ paketi varken).
@Entity
@Table(name = "business_closures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(name = "closure_date", nullable = false)
    private LocalDate date;

    // Neden kapalı olduğu (örn. "Ramazan Bayramı") — isteğe bağlı,
    // gösterim amaçlı, iş kuralı buna dayanmıyor.
    private String reason;
}
