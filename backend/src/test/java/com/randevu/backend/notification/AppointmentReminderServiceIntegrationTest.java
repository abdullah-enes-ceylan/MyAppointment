package com.randevu.backend.notification;

import com.randevu.backend.AbstractIntegrationTest;
import com.randevu.backend.entity.*;
import com.randevu.backend.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// Faz 3.4 -- PENDING_EXPIRY_WARNING'in (a) gercekten ISLETME SAHIBINE
// gittigini, (b) ayni talep icin iki kez gonderilmedigini (hem uygulama
// seviyesi on-filtre hem GERCEK DB kisiti seviyesinde), (c) talep
// onaylandiktan sonra bir daha hic denenmedigini kanitlar. Gercek Postgres
// sart -- partial unique index'in GERCEKTEN calistigini kanitlamanin tek
// yolu, mock'lanmis bir repository'de bu kisit hic yok.
class AppointmentReminderServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AppointmentReminderService appointmentReminderService;
    @Autowired
    private NotificationService notificationService;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private NotificationLogRepository notificationLogRepository;
    @Autowired
    private InAppNotificationRepository inAppNotificationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ServiceItemRepository serviceItemRepository;

    private Long ownerId;
    private Long customerId;

    // expiresAt'i "simdi"den ~5 dakika ileride uretecek sekilde kuruluyor:
    // appointmentDate = now + 65dk, createdAt = now - 12 saat (uzun
    // pencere, AppointmentExpiryPolicy'nin varsayilan 1 saatlik SABIT payi
    // devreye giriyor -- %10'luk oran degil, cunku pencerenin %10'u burada
    // 1 saatten fazla). expiresAt = appointmentDate - 1 saat = now + 5dk.
    // Varsayilan pendingWarningLeadTime (15dk) penceresi: expiresAt-15dk
    // <= now < expiresAt, yani "now + 5dk" bu araliga tam giriyor --
    // uyari zamani gelmis ama randevu henuz DUSMEMIS.
    private Appointment createPendingNearWarningWindow() {
        String unique = String.valueOf(System.nanoTime());
        User owner = userRepository.save(User.builder()
                .name("Sahip").surName("Test").email("owner-" + unique + "@test.com")
                .password("x").phone("0500").role(Role.BUSINESS_OWNER).build());
        User customer = userRepository.save(User.builder()
                .name("Musteri").surName("Test").email("customer-" + unique + "@test.com")
                .password("x").phone("0500").role(Role.USER).build());
        this.ownerId = owner.getId();
        this.customerId = customer.getId();

        Business business = businessRepository.save(Business.builder()
                .name("Test İşletme " + unique).address("Test Adres").owner(owner)
                .category(BusinessCategory.HAIRDRESSER).build());
        ServiceItem serviceItem = serviceItemRepository.save(ServiceItem.builder()
                .name("Sac Kesimi").description("Test").price(BigDecimal.valueOf(100))
                .durationInMinutes(30).business(business).build());

        Appointment appointment = Appointment.builder()
                .status(AppointmentStatus.PENDING)
                .business(business)
                .customer(customer)
                .serviceItem(serviceItem)
                .appointmentDate(LocalDateTime.now().plusMinutes(65))
                .createdAt(LocalDateTime.now().minusHours(12))
                .build();
        return appointmentRepository.save(appointment);
    }

    @Test
    @DisplayName("PENDING_EXPIRY_WARNING müşteriye değil İŞLETME SAHİBİNE gider")
    void uyariIsletmeSahibineGider() {
        Appointment appointment = createPendingNearWarningWindow();

        appointmentReminderService.sendPendingExpiryWarnings();

        List<InAppNotification> ownerNotifications = inAppNotificationRepository.findByRecipientUserId(ownerId);
        List<InAppNotification> customerNotifications = inAppNotificationRepository.findByRecipientUserId(customerId);

        assertThat(ownerNotifications).hasSize(1);
        assertThat(ownerNotifications.get(0).getTitle()).contains("düşmek üzere");
        assertThat(customerNotifications).isEmpty();

        boolean sentLogged = notificationLogRepository.existsByAppointmentIdAndNotificationTypeAndStatus(
                appointment.getId(), NotificationType.PENDING_EXPIRY_WARNING, NotificationStatus.SENT);
        assertThat(sentLogged).isTrue();
    }

    @Test
    @DisplayName("Idempotency (uygulama seviyesi): aynı taramayı iki kez çalıştırmak ikinci uyarı ÜRETMEZ")
    void ayniTaramaIkiKezCalistirilirsaIkinciUyariUretilmez() {
        Appointment appointment = createPendingNearWarningWindow();

        appointmentReminderService.sendPendingExpiryWarnings();
        appointmentReminderService.sendPendingExpiryWarnings(); // "aynı tick'i" simüle eder

        List<InAppNotification> ownerNotifications = inAppNotificationRepository.findByRecipientUserId(ownerId);
        assertThat(ownerNotifications).hasSize(1);

        // NOT: findAll() BILEREK kullanilmiyor -- AbstractIntegrationTest
        // container'i (ve dolayisiyla DB'yi) sinif icindeki TUM testler
        // arasinda paylasiyor, baska testlerin kayitlari findAll()'a
        // karisirdi. Bu yuzden SADECE bu testin kendi randevusuna gore
        // filtreleniyor.
        long sentCount = notificationLogRepository.findAll().stream()
                .filter(n -> n.getAppointmentId().equals(appointment.getId()))
                .filter(n -> n.getNotificationType() == NotificationType.PENDING_EXPIRY_WARNING)
                .filter(n -> n.getStatus() == NotificationStatus.SENT)
                .count();
        assertThat(sentCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Idempotency (DB seviyesi): partial unique index, uygulama filtresi baypas edilse bile ikinci SENT'i reddeder")
    void dbKisitiUygulamaFiltresiBaypasEdilseBileIkinciSentiReddeder() {
        // Kasten AppointmentReminderService.sendPendingExpiryWarnings()'in
        // on-filtresini ATLAYIP NotificationService.sendAndLog'u DOGRUDAN
        // iki kez cagiriyoruz -- amac uygulama mantiginin degil, GERCEK DB
        // kisitinin (V13'teki partial unique index) calistigini kanitlamak.
        Appointment appointment = createPendingNearWarningWindow();
        Notification notification = new Notification(ownerId, NotificationType.PENDING_EXPIRY_WARNING,
                "Bekleyen bir talebiniz düşmek üzere", "test");

        notificationService.sendAndLog(notification, appointment.getId());

        assertThat(catchDataIntegrityViolation(() ->
                notificationService.sendAndLog(notification, appointment.getId())))
                .isTrue();

        long sentCount = notificationLogRepository.findAll().stream()
                .filter(n -> n.getAppointmentId().equals(appointment.getId()))
                .filter(n -> n.getStatus() == NotificationStatus.SENT)
                .count();
        assertThat(sentCount).isEqualTo(1);
    }

    private boolean catchDataIntegrityViolation(Runnable action) {
        try {
            action.run();
            return false;
        } catch (DataIntegrityViolationException e) {
            return true;
        }
    }

    @Test
    @DisplayName("Idempotency (GERÇEK YARIŞ): iki eş zamanlı çalışma aynı randevu için AYNI ANDA dener, sadece biri SENT olarak kalır")
    void esZamanliIkiCalismaAyniRandevuyaSadeceBirKezSentYazar() throws InterruptedException {
        Appointment appointment = createPendingNearWarningWindow();
        Notification notification = new Notification(ownerId, NotificationType.PENDING_EXPIRY_WARNING,
                "Bekleyen bir talebiniz düşmek üzere", "test");

        int threadCount = 2;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger constraintViolationCount = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    notificationService.sendAndLog(notification, appointment.getId());
                    successCount.incrementAndGet();
                } catch (DataIntegrityViolationException e) {
                    constraintViolationCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // ikisini de AYNI ANDA serbest birak
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // ASIL KANIT: iki es zamanli deneme olmasina ragmen SADECE biri
        // basarili oldu, digeri DB kisitiyla reddedildi.
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(constraintViolationCount.get()).isEqualTo(1);

        long sentCount = notificationLogRepository.findAll().stream()
                .filter(n -> n.getAppointmentId().equals(appointment.getId()))
                .filter(n -> n.getStatus() == NotificationStatus.SENT)
                .count();
        assertThat(sentCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Talep ONAYLANDIKTAN sonra bir sonraki taramada bir daha hiç denenmez")
    void onaylananTalepBirDahaTaranmaz() {
        Appointment appointment = createPendingNearWarningWindow();

        appointmentReminderService.sendPendingExpiryWarnings();
        assertThat(inAppNotificationRepository.findByRecipientUserId(ownerId)).hasSize(1);

        // Isletme talebi onayliyor -- artik PENDING degil.
        appointment.setStatus(AppointmentStatus.APPROVED);
        appointmentRepository.save(appointment);

        // Bir sonraki "tick".
        appointmentReminderService.sendPendingExpiryWarnings();

        // Sorgu findByStatus(PENDING) uzerinden calistigi icin bu randevu
        // ADAY LISTESINE BILE GIRMEDI -- ikinci bir bildirim gitmedi.
        assertThat(inAppNotificationRepository.findByRecipientUserId(ownerId)).hasSize(1);
    }

    // appointmentDate = now + 1 saat: varsayilan reminderLeadTime (24 saat)
    // penceresi icinde (now < appointmentDate < now+24s) ve henuz GECMEMIS.
    private Appointment createApprovedNearReminderWindow() {
        String unique = String.valueOf(System.nanoTime());
        User owner = userRepository.save(User.builder()
                .name("Sahip").surName("Test").email("owner-" + unique + "@test.com")
                .password("x").phone("0500").role(Role.BUSINESS_OWNER).build());
        User customer = userRepository.save(User.builder()
                .name("Musteri").surName("Test").email("customer-" + unique + "@test.com")
                .password("x").phone("0500").role(Role.USER).build());
        this.ownerId = owner.getId();
        this.customerId = customer.getId();

        Business business = businessRepository.save(Business.builder()
                .name("Test İşletme " + unique).address("Test Adres").owner(owner)
                .category(BusinessCategory.HAIRDRESSER).build());
        ServiceItem serviceItem = serviceItemRepository.save(ServiceItem.builder()
                .name("Sac Kesimi").description("Test").price(BigDecimal.valueOf(100))
                .durationInMinutes(30).business(business).build());

        Appointment appointment = Appointment.builder()
                .status(AppointmentStatus.APPROVED)
                .business(business)
                .customer(customer)
                .serviceItem(serviceItem)
                .appointmentDate(LocalDateTime.now().plusHours(1))
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();
        return appointmentRepository.save(appointment);
    }

    @Test
    @DisplayName("APPOINTMENT_REMINDER (24 saat) MÜŞTERİYE gider, işletme sahibine değil")
    void hatirlatmaMusteriyeGider() {
        Appointment appointment = createApprovedNearReminderWindow();

        appointmentReminderService.sendUpcomingApprovedReminders();

        List<InAppNotification> customerNotifications = inAppNotificationRepository.findByRecipientUserId(customerId);
        List<InAppNotification> ownerNotifications = inAppNotificationRepository.findByRecipientUserId(ownerId);

        assertThat(customerNotifications).hasSize(1);
        assertThat(customerNotifications.get(0).getTitle()).isEqualTo("Yaklaşan randevunuz");
        assertThat(ownerNotifications).isEmpty();

        boolean sentLogged = notificationLogRepository.existsByAppointmentIdAndNotificationTypeAndStatus(
                appointment.getId(), NotificationType.APPOINTMENT_REMINDER, NotificationStatus.SENT);
        assertThat(sentLogged).isTrue();
    }

    @Test
    @DisplayName("APPOINTMENT_REMINDER idempotency: aynı taramayı iki kez çalıştırmak ikinci hatırlatma ÜRETMEZ")
    void hatirlatmaIkiKezTaranirsaTekGonderilir() {
        Appointment appointment = createApprovedNearReminderWindow();

        appointmentReminderService.sendUpcomingApprovedReminders();
        appointmentReminderService.sendUpcomingApprovedReminders(); // "aynı tick'i" simüle eder

        assertThat(inAppNotificationRepository.findByRecipientUserId(customerId)).hasSize(1);

        long sentCount = notificationLogRepository.findAll().stream()
                .filter(n -> n.getAppointmentId().equals(appointment.getId()))
                .filter(n -> n.getNotificationType() == NotificationType.APPOINTMENT_REMINDER)
                .filter(n -> n.getStatus() == NotificationStatus.SENT)
                .count();
        assertThat(sentCount).isEqualTo(1);
    }
}
