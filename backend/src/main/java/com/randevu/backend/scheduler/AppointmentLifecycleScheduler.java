package com.randevu.backend.scheduler;

import com.randevu.backend.service.AppointmentService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Randevu yasam dongusunun arka plan bakimi. Iki is yapiyor: suresi gecmis
// onayli randevulari tamamlamak (Faz 2.2) ve cevaplanmamis talepleri
// dusurmek.
//
// Eskiden adi AppointmentCompletionScheduler'di; ikinci is eklenince ad
// yaniltici hale geldi (sinif artik sadece "tamamlama" yapmiyor).
//
// Her iki metodun da tek isi TETIKLEME -- ne zaman calisacagini belirlemek.
// Is mantigi AppointmentService'te, dusme ani hesabi ise ondan da ayri bir
// saf sinifta (AppointmentExpiryPolicy). SRP: bu sinif "ne zaman", servis
// "ne yapilacak", policy "kural nedir" sorusunu cevapliyor.
@Component
public class AppointmentLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentLifecycleScheduler.class);

    // Iki gorev de AYNI araligi paylasiyor ve placeholder TEK yerde
    // yaziyor. Ayni metni iki @Scheduled'a ayri ayri yazmak, ileride
    // birinin guncellenip digerinin unutulmasina acik kapi birakirdi.
    // Anotasyon degeri derleme zamani sabiti olmak zorunda; literal ile
    // baslatilan static final String bu sarti sagliyor.
    private static final String SCHEDULER_CRON = "${app.appointment.scheduler-cron:0 */5 * * * *}";

    private final AppointmentService appointmentService;

    public AppointmentLifecycleScheduler(AppointmentService appointmentService) {
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
    @Scheduled(cron = SCHEDULER_CRON)
    public void completeElapsedAppointments() {
        int completedCount = appointmentService.completeElapsedAppointments();
        if (completedCount > 0) {
            log.info("Otomatik tamamlama: {} randevu COMPLETED durumuna geçti.", completedCount);
        }
    }

    // Ayni aralikta calisir. 5 dakikalik granulerlik dusme aninin +-5 dakika
    // kaymasi anlamina geliyor; kisa pencerelerde (30 dakikalik randevu icin
    // 3 dakikalik pay) bu oransal olarak buyuk bir sapma ama pratikte
    // zararsiz -- bilerek kabul edildi. Daha sik calistirmak, kazandirdigi
    // hassasiyetten fazla veritabani yuku getirirdi.
    @Scheduled(cron = SCHEDULER_CRON)
    public void expireStaleRequests() {
        int expiredCount = appointmentService.expireStaleRequests();
        if (expiredCount > 0) {
            log.info("Zaman aşımı: {} randevu talebi EXPIRED durumuna geçti.", expiredCount);
        }
    }
}
