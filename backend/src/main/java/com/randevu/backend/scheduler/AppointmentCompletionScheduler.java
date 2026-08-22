package com.randevu.backend.scheduler;

import com.randevu.backend.service.AppointmentService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Faz 2.2: isletme sahibi her randevuyu elle "tamamlandi" diye isaretlemek
// zorunda kalmasin diye, suresi gecmis APPROVED randevulari otomatik
// COMPLETED'a ceviren arka plan gorevi. Asil is mantigi
// AppointmentService.completeElapsedAppointments'ta -- bu sinifin tek isi
// TETIKLEME (ne zaman calisacagini belirlemek), SRP geregi is mantigina
// karismiyor; ayni sekilde AppointmentController'in HTTP çevirisi yapip
// is mantigina karismamasi gibi.
@Component
public class AppointmentCompletionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentCompletionScheduler.class);

    private final AppointmentService appointmentService;

    public AppointmentCompletionScheduler(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    // 5 dakikada bir calisir. Randevu saatlerinin saniye hassasiyetinde
    // olmasi gerekmiyor -- 5 dakikalik gecikme, "musteri gelmedi" butonuyla
    // (Faz 2.1) manuel mudahale arasinda makul bir denge: cok sik calisirsa
    // (ornegin her saniye) gereksiz veritabani yuku, cok seyrek calisirsa
    // (ornegin saatte bir) musteri "Randevularim" ekraninda tamamlanmis bir
    // randevunun uzun sure hala "Onaylandi" gorunmesi. fixedDelay degil
    // cron kullanildi: fixedDelay onceki calisma bitince sayar, bu isin
    // "saat baginda" calismasi onemli degil ama okunabilirlik icin cron
    // ifadesi (her 5 dakikada bir) tercih edildi.
    @Scheduled(cron = "0 */5 * * * *")
    public void completeElapsedAppointments() {
        int completedCount = appointmentService.completeElapsedAppointments();
        if (completedCount > 0) {
            log.info("Otomatik tamamlama: {} randevu COMPLETED durumuna geçti.", completedCount);
        }
    }
}
