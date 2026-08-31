package com.randevu.backend.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Kanal-bagimsizligin somut kaniti: app.notification.adapter=log yazilinca
// aktif olan implementasyon budur, InAppNotificationAdapter'in yerine
// GECER -- onu cagiran hicbir kod (NotificationService, AppointmentReminderService,
// AppointmentNotificationListener) degismez. Hicbir kalici veri uretmez,
// sadece loglar -- gecici bir dev/debug kanali. Body'yi TAM loglamiyoruz
// (sadece title) -- Faz 3.6'daki "loglarda PII maskelenir" karariyla
// simdiden tutarli kalmak icin, randevu detayi tasiyabilecek govdeyi
// bastan disariya cikarmiyoruz.
@Component
@ConditionalOnProperty(prefix = "app.notification", name = "adapter", havingValue = "log")
public class LoggingNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAdapter.class);

    @Override
    public void send(Notification notification) {
        log.info("[BILDIRIM] type={} recipientUserId={} title={}",
                notification.type(), notification.recipientUserId(), notification.title());
    }
}
