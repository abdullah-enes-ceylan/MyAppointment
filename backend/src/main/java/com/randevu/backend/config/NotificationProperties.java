package com.randevu.backend.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

// AppointmentPolicyProperties ile AYNI desen: is kurali sayisi koda
// gomulmez, gecersiz deger uygulamayi durdurmaz, guvenli varsayilana
// dusup uyari loglar.
@Component
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

    private static final Logger log = LoggerFactory.getLogger(NotificationProperties.class);

    private static final Duration DEFAULT_PENDING_WARNING_LEAD_TIME = Duration.ofMinutes(15);
    private static final Duration DEFAULT_REMINDER_LEAD_TIME = Duration.ofHours(24);

    // PENDING_EXPIRY_WARNING, AppointmentExpiryPolicy.expiresAt()'ten BU
    // KADAR once tetiklenir. 15 dakika varsayilan olarak secildi: normal
    // (1 saatlik sabit pay) pencerelerde "dusmeden ~45 dk once" gibi
    // makul bir uyari noktasina denk dusuyor. Cok kisa pencerelerde
    // (ornegin 30 dk'lik randevu icin ~3 dk'lik pay) bu deger pencereden
    // BUYUK kalabilir -- bu bir hata degil, BEKLENEN davranis: uyari o
    // durumda talebin olusturulmasindan neredeyse hemen sonra gider,
    // cunku pencere ne kadar kisaysa aciliyet o kadar yuksek.
    private Duration pendingWarningLeadTime = DEFAULT_PENDING_WARNING_LEAD_TIME;

    // APPOINTMENT_REMINDER, randevu saatinden BU KADAR once musteriye
    // gider. ROADMAP'in orijinal "24 saat hatirlatma" maddesi -- ayardan
    // geliyor ki sahadan "24 saat cok, 12 olsun" gibi geri bildirim gelirse
    // kod degisikligi gerekmesin (AppointmentPolicyProperties ile ayni
    // gerekce).
    private Duration reminderLeadTime = DEFAULT_REMINDER_LEAD_TIME;

    @PostConstruct
    public void validate() {
        if (pendingWarningLeadTime == null || pendingWarningLeadTime.isNegative() || pendingWarningLeadTime.isZero()) {
            log.warn("app.notification.pending-warning-lead-time geçersiz ({}), varsayılan {} kullanılıyor",
                    pendingWarningLeadTime, DEFAULT_PENDING_WARNING_LEAD_TIME);
            pendingWarningLeadTime = DEFAULT_PENDING_WARNING_LEAD_TIME;
        }
        if (reminderLeadTime == null || reminderLeadTime.isNegative() || reminderLeadTime.isZero()) {
            log.warn("app.notification.reminder-lead-time geçersiz ({}), varsayılan {} kullanılıyor",
                    reminderLeadTime, DEFAULT_REMINDER_LEAD_TIME);
            reminderLeadTime = DEFAULT_REMINDER_LEAD_TIME;
        }
    }

    public Duration getPendingWarningLeadTime() {
        return pendingWarningLeadTime;
    }

    public void setPendingWarningLeadTime(Duration pendingWarningLeadTime) {
        this.pendingWarningLeadTime = pendingWarningLeadTime;
    }

    public Duration getReminderLeadTime() {
        return reminderLeadTime;
    }

    public void setReminderLeadTime(Duration reminderLeadTime) {
        this.reminderLeadTime = reminderLeadTime;
    }
}
