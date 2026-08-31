package com.randevu.backend.notification;

import com.randevu.backend.entity.NotificationLog;
import com.randevu.backend.entity.NotificationStatus;
import com.randevu.backend.entity.NotificationType;
import com.randevu.backend.repository.NotificationLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

// Bir bildirimi GONDERIP SONUCUNU KAYDETMENIN tek yeri -- cagiran taraf
// (AppointmentNotificationListener, ileride AppointmentReminderService)
// sadece "hangi bildirim, hangi randevu icin" bilgisini verir, SENT/FAILED
// ayrimini ve notification_log yazimini burasi yapar.
//
// Propagation.REQUIRES_NEW iki metotta da BILINCLI: bu servis hem
// AFTER_COMMIT'te (ambient transaction YOK, REQUIRES_NEW zaten fark
// etmez) hem ileride periyodik bir tarama dongusunun ICINDEN (ambient
// transaction VAR) cagrilacak. Ikinci durumda REQUIRES_NEW olmasaydi,
// bir randevunun bildirim hatasi ayni dongudeki diger randevularin
// islemini de etkileyebilirdi -- her randevunun gonderim sonucu digerinden
// TAMAMEN bagimsiz commit olmali.
@Service
public class NotificationService {

    private final NotificationPort notificationPort;
    private final NotificationLogRepository notificationLogRepository;
    private final Clock clock;

    public NotificationService(NotificationPort notificationPort,
            NotificationLogRepository notificationLogRepository, Clock clock) {
        this.notificationPort = notificationPort;
        this.notificationLogRepository = notificationLogRepository;
        this.clock = clock;
    }

    // Gonderir ve basariliysa SENT loglar. NotificationDeliveryException
    // disariya firlatilir (yutulmaz) -- cagiran taraf bunu yakalayip
    // logFailure'i AYRI bir cagriyla tetiklemekten sorumlu (bkz. o
    // metodun aciklamasi: ayni transaction'da olsaydi, SENT denemesinin
    // rollback'i FAILED kaydini da silerdi).
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendAndLog(Notification notification, Long appointmentId) {
        notificationPort.send(notification);

        notificationLogRepository.save(NotificationLog.builder()
                .appointmentId(appointmentId)
                .notificationType(notification.type())
                .recipientUserId(notification.recipientUserId())
                .status(NotificationStatus.SENT)
                .attemptedAt(LocalDateTime.now(clock))
                .build());
    }

    // sendAndLog basarisiz olduktan SONRA, cagiran tarafindan ayrica
    // cagrilir. Kendi transaction'inda calisir ki sendAndLog'un rollback
    // olan transaction'i bu kaydi silmesin.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(Long appointmentId, NotificationType type, Long recipientUserId, String errorMessage) {
        notificationLogRepository.save(NotificationLog.builder()
                .appointmentId(appointmentId)
                .notificationType(type)
                .recipientUserId(recipientUserId)
                .status(NotificationStatus.FAILED)
                .attemptedAt(LocalDateTime.now(clock))
                .errorMessage(errorMessage)
                .build());
    }
}
