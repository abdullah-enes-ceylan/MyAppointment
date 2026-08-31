package com.randevu.backend.notification;

import com.randevu.backend.config.NotificationProperties;
import com.randevu.backend.entity.Appointment;
import com.randevu.backend.entity.AppointmentStatus;
import com.randevu.backend.entity.NotificationStatus;
import com.randevu.backend.entity.NotificationType;
import com.randevu.backend.repository.AppointmentRepository;
import com.randevu.backend.repository.NotificationLogRepository;
import com.randevu.backend.service.AppointmentExpiryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Iki periyodik tarama burada yasiyor: PENDING_EXPIRY_WARNING (isletme
// sahibine) ve APPOINTMENT_REMINDER (musteriye, ROADMAP'in orijinal "24
// saat hatirlatma" maddesi). AppointmentReminderScheduler ikisini de AYNI
// tick'te tetikler (bkz. o sinif, "ne zaman" sorusunu o cevaplar, bu sinif
// "kimi, ne zaman uyaracagini" cozer).
//
// Ikisi de AYNI cron'u paylasiyor cunku PENDING_EXPIRY_WARNING'in zaman
// hassasiyeti (dakikalar mertebesine inebilen pencereler) APPOINTMENT_
// REMINDER icin de fazlasiyla yeterli -- sik kontrol 24 saatlik bir
// hatirlatma icin zararsiz, sadece cogu tick'te aday bulamayip hicbir sey
// yapmadan doner.
//
// APPOINTMENT_EXPIRED'dan (event-tabanli, bkz. AppointmentNotificationListener)
// FARKLI bir tetikleme sekli KASITLI: o bir DURUM GECISINE bagli ("randevu
// simdi EXPIRED oldu"), bu ikisi ise sadece ZAMANIN GECMESINE bagli (hicbir
// sey "olmuyor", sadece bir zaman noktasina yaklasiliyor) -- bu yuzden hala
// periyodik tarama gerekiyor, event yayinlanacak bir an yok.
@Service
public class AppointmentReminderService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderService.class);

    private final AppointmentRepository appointmentRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationService notificationService;
    private final AppointmentExpiryPolicy expiryPolicy;
    private final NotificationProperties notificationProperties;
    private final Clock clock;

    public AppointmentReminderService(AppointmentRepository appointmentRepository,
            NotificationLogRepository notificationLogRepository,
            NotificationService notificationService,
            AppointmentExpiryPolicy expiryPolicy,
            NotificationProperties notificationProperties,
            Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.notificationService = notificationService;
        this.expiryPolicy = expiryPolicy;
        this.notificationProperties = notificationProperties;
        this.clock = clock;
    }

    // Aday sorgusu ISLETME sahibinin daha once cevaplamadigi (hala PENDING)
    // talepleri tarar. "artik PENDING degil" (APPROVED/REJECTED/CANCELLED/
    // EXPIRED) olan bir randevu bu sorguya HIC girmez -- isletme talebi
    // onayladiktan sonraki tick'te bu randevu adaylar listesinde bile
    // olusmuyor, ayrica bir "iptal et" mantigina gerek kalmiyor.
    public void sendPendingExpiryWarnings() {
        LocalDateTime now = LocalDateTime.now(clock);
        Duration leadTime = notificationProperties.getPendingWarningLeadTime();

        List<Appointment> candidates = appointmentRepository.findByStatus(AppointmentStatus.PENDING).stream()
                .filter(a -> isWithinWarningWindow(a, now, leadTime))
                .filter(a -> !notificationLogRepository.existsByAppointmentIdAndNotificationTypeAndStatus(
                        a.getId(), NotificationType.PENDING_EXPIRY_WARNING, NotificationStatus.SENT))
                .toList();

        candidates.forEach(a -> sendWarning(a, notification(a)));
    }

    // expiresAt - leadTime <= now < expiresAt: uyari penceresi icinde ama
    // henuz gercekten dusmemis. isExpired kontrolu BILEREK ayrica var --
    // AppointmentLifecycleScheduler'in kendi tick'i (ayri, 5 dakikalik cron)
    // henuz yetismemis olabilir; boyle bir randevuya "dusmek uzere" uyarisi
    // göndermek yanlis olur, zaten dusmus.
    private boolean isWithinWarningWindow(Appointment appointment, LocalDateTime now, Duration leadTime) {
        LocalDateTime createdAt = appointment.getCreatedAt();
        LocalDateTime appointmentDate = appointment.getAppointmentDate();

        if (expiryPolicy.isExpired(createdAt, appointmentDate, now)) {
            return false;
        }
        LocalDateTime expiresAt = expiryPolicy.expiresAt(createdAt, appointmentDate);
        return !now.isBefore(expiresAt.minus(leadTime));
    }

    private Notification notification(Appointment appointment) {
        return new Notification(
                appointment.getBusiness().getOwner().getId(),
                NotificationType.PENDING_EXPIRY_WARNING,
                "Bekleyen bir talebiniz düşmek üzere",
                "İstek kutunuzdaki bir talep yakında zaman aşımına uğrayacak. Cevaplamak için son şansınız.");
    }

    // ROADMAP'in orijinal Faz 3.4 maddesi: MUSTERIYE, onayli randevusu
    // yaklasirken hatirlatma. Aday sorgusu APPROVED'i tarar -- PENDING veya
    // baska bir durumdaki randevu icin hatirlatma anlamsiz olurdu (henuz
    // kesinlesmemis bir randevuyu "yaklasiyor" diye hatirlatmak yanlis
    // bilgi verir).
    public void sendUpcomingApprovedReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        Duration leadTime = notificationProperties.getReminderLeadTime();

        List<Appointment> candidates = appointmentRepository.findByStatus(AppointmentStatus.APPROVED).stream()
                .filter(a -> isWithinReminderWindow(a, now, leadTime))
                .filter(a -> !notificationLogRepository.existsByAppointmentIdAndNotificationTypeAndStatus(
                        a.getId(), NotificationType.APPOINTMENT_REMINDER, NotificationStatus.SENT))
                .toList();

        candidates.forEach(a -> sendWarning(a, reminderNotification(a)));
    }

    // now < appointmentDate < now + leadTime: randevu henuz GECMEMIS ve
    // pencere icinde. appointmentDate.isAfter(now) sarti BILEREK var --
    // is bir sure calismamissa (restart, deploy) GECMIS bir randevu icin
    // "yaklasiyor" hatirlatmasi gondermeyelim.
    private boolean isWithinReminderWindow(Appointment appointment, LocalDateTime now, Duration leadTime) {
        LocalDateTime appointmentDate = appointment.getAppointmentDate();
        return appointmentDate.isAfter(now) && appointmentDate.isBefore(now.plus(leadTime));
    }

    private static final DateTimeFormatter REMINDER_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private Notification reminderNotification(Appointment appointment) {
        String when = appointment.getAppointmentDate().format(REMINDER_DATE_FORMAT);
        String businessName = appointment.getBusiness().getName();
        return new Notification(
                appointment.getCustomer().getId(),
                NotificationType.APPOINTMENT_REMINDER,
                "Yaklaşan randevunuz",
                businessName + " işletmesinde " + when + " tarihli randevunuz yaklaşıyor.");
    }

    // sendPendingExpiryWarnings ve sendUpcomingApprovedReminders'in ORTAK
    // gonderim/hata-yonetimi -- iki tarama da AYNI kurallara tabi (bkz.
    // NotificationService.sendAndLog'daki REQUIRES_NEW gerekcesi): basarili
    // olursa SENT loglanir, DB kisiti (es zamanli baska bir calisma zaten
    // gondermis) FAILED SAYILMAZ, gercek bir gonderim hatasi FAILED
    // loglanir.
    private void sendWarning(Appointment appointment, Notification notification) {
        try {
            notificationService.sendAndLog(notification, appointment.getId());
        } catch (DataIntegrityViolationException e) {
            // Baska bir es zamanli calisma (ornegin coklu uygulama
            // instance'i) bu bildirimi bizden ONCE gonderip commit etmis --
            // partial unique index (bkz. V13) bunu yakaladi. Bu bir
            // BASARISIZLIK degil, ayni isin zaten yapilmis olmasi -- FAILED
            // loglamiyoruz, cunku gercekte gonderim basarili oldu (baskasi
            // tarafindan).
            log.debug("{} zaten başka bir çalışma tarafından gönderilmiş (randevu {})",
                    notification.type(), appointment.getId());
        } catch (NotificationDeliveryException e) {
            log.warn("{} gönderilemedi (randevu {}): {}", notification.type(), appointment.getId(), e.getMessage());
            notificationService.logFailure(appointment.getId(), notification.type(), notification.recipientUserId(),
                    e.getMessage());
        }
    }
}
