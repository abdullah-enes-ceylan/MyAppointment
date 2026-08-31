package com.randevu.backend.scheduler;

import com.randevu.backend.notification.AppointmentReminderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// AppointmentLifecycleScheduler'dan (5 dakika) BILEREK AYRI bir cron
// kullaniyor, ondan daha sik (varsayilan: her dakika). Sebep: bir randevu
// talebinin dusme penceresi 3 dakikaya kadar inebiliyor (bkz.
// AppointmentExpiryPolicy'nin kisa-pencere testleri); 5 dakikalik bir
// tarama araligi bu uyariyi tamamen kacirabilir ya da randevu zaten
// dustukten SONRA gonderebilirdi. Sinif AppointmentLifecycleScheduler'a
// EKLENMEDI -- o sinif zaten bir kere ("ikinci is eklenince adi yaniltici
// hale geldi") bu sebeple yeniden adlandirilmisti, uc uncu (ve kavramsal
// olarak farkli: durum gecisi degil bildirim) bir isi ayni sinifa eklemek
// ayni hatayi tekrarlardi.
@Component
public class AppointmentReminderScheduler {

    private static final String REMINDER_CRON = "${app.notification.reminder-cron:0 * * * * *}";

    private final AppointmentReminderService appointmentReminderService;

    public AppointmentReminderScheduler(AppointmentReminderService appointmentReminderService) {
        this.appointmentReminderService = appointmentReminderService;
    }

    // Iki tarama da AYNI tick'te calisir -- PENDING_EXPIRY_WARNING'in
    // gerektirdigi sik kontrol (bkz. yukaridaki gerekce) APPOINTMENT_
    // REMINDER icin de fazlasiyla yeterli, ayri bir cron'a gerek yok.
    @Scheduled(cron = REMINDER_CRON)
    public void runNotificationChecks() {
        appointmentReminderService.sendPendingExpiryWarnings();
        appointmentReminderService.sendUpcomingApprovedReminders();
    }
}
