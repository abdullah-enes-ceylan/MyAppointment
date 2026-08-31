package com.randevu.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

// Bildirim gonderim/idempotency gunlugu (Faz 3.4). Hangi NotificationPort
// implementasyonu aktif olursa olsun her zaman yazilir -- kanaldan
// bagimsiz. appointment_id + notification_type + status='SENT' uzerindeki
// partial unique index (bkz. V13 migration) ayni bildirimin iki kez
// gonderilmesini DB seviyesinde engelliyor; bu entity sadece o kisitin
// Java tarafi.
@Entity
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    // Clock'tan -- bu siniftaki hicbir yer ciplak now() cagirmiyor,
    // deger daima cagiran tarafta (NotificationService) enjekte edilen
    // Clock'tan uretiliyor.
    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    // Sadece FAILED'ta dolu. Ic detay/stack trace degil, kisa bir mesaj --
    // bu tablo hicbir zaman istemciye donmuyor (sadece ops/debug icin),
    // yine de GlobalExceptionHandler'daki "asla stack trace sizdirma"
    // disipliniyle tutarli kalsin diye kisa tutuluyor.
    @Column(name = "error_message", length = 500)
    private String errorMessage;
}
