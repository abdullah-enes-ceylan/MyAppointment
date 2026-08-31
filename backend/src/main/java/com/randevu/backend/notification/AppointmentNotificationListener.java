package com.randevu.backend.notification;

import com.randevu.backend.entity.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// AppointmentExpiredEvent'i dinleyip musteriye APPOINTMENT_EXPIRED
// bildirimi gonderir.
//
// TransactionPhase.AFTER_COMMIT KRITIK, duz @EventListener DEGIL: duz
// @EventListener, event'i AppointmentService.expireStaleRequests()'in
// transaction'i HENUZ COMMIT OLMADAN, ayni transaction icinde senkron
// calistirirdi -- bu durumda NotificationDeliveryException disariya
// firlarsa, o exception expireStaleRequests'in cagri yigitina geri
// yayilip TUM transaction'i (randevunun EXPIRED'a gecisi dahil) geri
// alirdi. Bildirim gonderiminin basarisiz olmasi, randevunun gercekten
// dusmus olma gercegini DEGISTIRMEMELI -- musteri zaten cevap alamadi,
// bunu bir de bildirim altyapisinin hatasi yuzunden "hala PENDING"
// yapmak cok daha kotu bir sonuc olurdu.
//
// AFTER_COMMIT ile bu dinleyici SADECE ilgili transaction GERCEKTEN VE
// KALICI OLARAK commit olduktan sonra calisiyor -- o noktada
// expireStaleRequests'in transaction'inin geri alinma ihtimali sifir
// (zaten kapandi), bu yuzden burada olacak HERHANGI bir hata randevunun
// durumunu asla etkileyemez.
@Component
public class AppointmentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationListener.class);

    private final NotificationService notificationService;

    public AppointmentNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentExpired(AppointmentExpiredEvent event) {
        Notification notification = new Notification(
                event.customerId(),
                NotificationType.APPOINTMENT_EXPIRED,
                "Randevu talebiniz düştü",
                "Talebiniz zamanında cevaplanmadığı için düştü. Başka bir saat seçebilirsiniz.");

        try {
            notificationService.sendAndLog(notification, event.appointmentId());
        } catch (DataIntegrityViolationException e) {
            // Coklu instance senaryosunda ayni event iki kez islenmis
            // olabilir (ornegin her instance kendi AppointmentLifecycleScheduler'ini
            // calistirdiginda) -- partial unique index bunu yakaladi,
            // gercekte gonderim basarili oldu (baskasi tarafindan), FAILED
            // loglamiyoruz. bkz. AppointmentReminderService'teki ayni gerekce.
            log.debug("APPOINTMENT_EXPIRED zaten başka bir çalışma tarafından gönderilmiş (randevu {})",
                    event.appointmentId());
        } catch (NotificationDeliveryException e) {
            log.warn("APPOINTMENT_EXPIRED bildirimi gönderilemedi (randevu {}): {}",
                    event.appointmentId(), e.getMessage());
            notificationService.logFailure(event.appointmentId(), NotificationType.APPOINTMENT_EXPIRED,
                    event.customerId(), e.getMessage());
        }
    }
}
