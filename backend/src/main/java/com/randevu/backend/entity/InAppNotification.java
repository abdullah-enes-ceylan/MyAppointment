package com.randevu.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

// Kullanicinin uygulama ici bildirim kutusu (Faz 3.4). notification_log'dan
// (icsel idempotency gunlugu) FARKLI bir tablo -- bkz. V14 migration'daki
// aciklama. Sadece InAppNotificationAdapter yazar.
@Entity
@Table(name = "in_app_notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InAppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    // Clock'tan -- InAppNotificationAdapter enjekte edilen Clock'u kullanir.
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
