package com.randevu.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Kullanıcı-işletme favori ilişkisi. Review'daki aynı desen: aradaki
// bağ kendi entity'si -- (user_id, business_id) üzerindeki UNIQUE
// kısıt (bkz. V9 migration) "aynı işletme iki kez favoriye eklenemez"
// kuralının asıl garantisi, servis katmanı değil.
@Entity
@Table(name = "favorites")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
