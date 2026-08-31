package com.randevu.backend.notification;

import com.randevu.backend.entity.InAppNotification;
import com.randevu.backend.repository.InAppNotificationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

// Faz 3.4'un varsayilan (gercek, kullanilabilir) kanali -- app.notification.
// adapter tanimsizsa ya da "in-app" ise bu aktif olur (matchIfMissing=true).
// LoggingNotificationAdapter'dan farki: kalici bir satir uretiyor, ileride
// frontend'de zil ikonu bunu okuyabilir.
@Component
@ConditionalOnProperty(prefix = "app.notification", name = "adapter", havingValue = "in-app", matchIfMissing = true)
public class InAppNotificationAdapter implements NotificationPort {

    private final InAppNotificationRepository inAppNotificationRepository;
    private final Clock clock;

    public InAppNotificationAdapter(InAppNotificationRepository inAppNotificationRepository, Clock clock) {
        this.inAppNotificationRepository = inAppNotificationRepository;
        this.clock = clock;
    }

    @Override
    public void send(Notification notification) {
        inAppNotificationRepository.save(InAppNotification.builder()
                .recipientUserId(notification.recipientUserId())
                .title(notification.title())
                .body(notification.body())
                .createdAt(LocalDateTime.now(clock))
                .build());
    }
}
