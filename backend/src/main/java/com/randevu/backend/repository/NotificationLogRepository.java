package com.randevu.backend.repository;

import com.randevu.backend.entity.NotificationLog;
import com.randevu.backend.entity.NotificationStatus;
import com.randevu.backend.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    // Idempotency kontrolu: bu randevuya bu turden zaten BASARIYLA
    // gonderilmis mi. DB'deki partial unique index (sadece status='SENT')
    // asil garanti -- bu sorgu ise gereksiz deneme/loglama trafigini
    // onceden elemek icin ucuz bir on-filtre.
    boolean existsByAppointmentIdAndNotificationTypeAndStatus(
            Long appointmentId, NotificationType notificationType, NotificationStatus status);
}
