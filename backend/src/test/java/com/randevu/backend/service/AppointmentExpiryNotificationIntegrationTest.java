package com.randevu.backend.service;

import com.randevu.backend.AbstractIntegrationTest;
import com.randevu.backend.entity.*;
import com.randevu.backend.notification.Notification;
import com.randevu.backend.notification.NotificationDeliveryException;
import com.randevu.backend.notification.NotificationPort;
import com.randevu.backend.repository.AppointmentRepository;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.NotificationLogRepository;
import com.randevu.backend.repository.ServiceItemRepository;
import com.randevu.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

// Faz 3.4 -- bu testin TEK amaci, AppointmentNotificationListener'daki
// AFTER_COMMIT tercihinin gercekten iddia edilen seyi yaptigini KANITLAMAK,
// varsayim olarak birakmamak. Gercek Postgres (AbstractIntegrationTest)
// sart: transaction commit/rollback semantigi mock'lanmis bir repository
// ile dogru sekilde gosterilemez.
//
// NotificationPort'u kasitli olarak patlayabilen bir sahte implementasyonla
// (@Primary @TestConfiguration) degistiriyoruz -- LoggingNotificationAdapter
// gercek loglama yapar ama "basarisiz oldu" durumunu deterministik
// tetikleyemeyiz, bu yuzden kontrol edilebilir bir sahte gerekiyor.
class AppointmentExpiryNotificationIntegrationTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class ControllableNotificationPortConfig {
        @Bean
        @Primary
        NotificationPort controllableNotificationPort() {
            return new ControllableNotificationPort();
        }
    }

    static class ControllableNotificationPort implements NotificationPort {
        final AtomicBoolean shouldFail = new AtomicBoolean(false);
        final List<Notification> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(Notification notification) {
            if (shouldFail.get()) {
                throw new NotificationDeliveryException("kasıtlı test hatası");
            }
            sent.add(notification);
        }
    }

    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private NotificationLogRepository notificationLogRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ServiceItemRepository serviceItemRepository;
    @Autowired
    private ControllableNotificationPort notificationPort;

    // Gercek sistem saatine gore kuruluyor (Clock bean'i fixed degil, bkz.
    // TimeConfig) -- appointmentDate'i cok yakina, createdAt'i cok geriye
    // koyarak AppointmentExpiryPolicy'nin GERCEK varsayilan ayarlariyla
    // (1 saat sabit pay, %10 oran) "zaten dusmus" cikmasini garantiliyoruz,
    // hangi anda calistirilirsa calistirilsin.
    private Appointment createAboutToExpireAppointment() {
        String unique = String.valueOf(System.nanoTime());
        User owner = userRepository.save(User.builder()
                .name("Sahip").surName("Test").email("owner-" + unique + "@test.com")
                .password("x").phone("0500").role(Role.BUSINESS_OWNER).build());
        User customer = userRepository.save(User.builder()
                .name("Musteri").surName("Test").email("customer-" + unique + "@test.com")
                .password("x").phone("0500").role(Role.USER).build());
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
                .appointmentDate(LocalDateTime.now().plusMinutes(1))
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();
        return appointmentRepository.save(appointment);
    }

    @Test
    @DisplayName("Bildirim BAŞARILI: randevu EXPIRED olur, notification_log'a SENT yazılır")
    void basariliGonderim_randevuExpiredOlurVeSentLoglanir() {
        notificationPort.shouldFail.set(false);
        Appointment appointment = createAboutToExpireAppointment();

        appointmentService.expireStaleRequests();

        // AFTER_COMMIT dinleyicisi expireStaleRequests'in transaction'i
        // commit olduktan hemen sonra, ayni thread'de SENKRON calisir
        // (bkz. AppointmentNotificationListener) -- yani expireStaleRequests
        // metodu donduğunde bildirim islemi de zaten tamamlanmis olur,
        // ayrica beklemeye gerek yok.
        Appointment reloaded = appointmentRepository.findById(appointment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AppointmentStatus.EXPIRED);

        boolean sentLogged = notificationLogRepository.existsByAppointmentIdAndNotificationTypeAndStatus(
                appointment.getId(), NotificationType.APPOINTMENT_EXPIRED, NotificationStatus.SENT);
        assertThat(sentLogged).isTrue();
    }

    @Test
    @DisplayName("Bildirim BAŞARISIZ: randevu YİNE DE EXPIRED kalır, FAILED loglanır, hiçbir şey rollback olmaz")
    void basarisizGonderim_randevuYineDeExpiredKalirVeFailedLoglanir() {
        notificationPort.shouldFail.set(true);
        Appointment appointment = createAboutToExpireAppointment();

        // Kilit iddia: expireStaleRequests bildirim patlasa bile HATA
        // FIRLATMAZ -- cunku dinleyici AFTER_COMMIT'te calisiyor, bu
        // metodun kendi transaction'i o noktada zaten kapanmis oluyor.
        appointmentService.expireStaleRequests();

        Appointment reloaded = appointmentRepository.findById(appointment.getId()).orElseThrow();
        // ASIL KANIT: bildirim basarisiz olmasina RAGMEN randevu hala
        // EXPIRED -- PENDING'e falan geri donmedi.
        assertThat(reloaded.getStatus()).isEqualTo(AppointmentStatus.EXPIRED);

        boolean failedLogged = notificationLogRepository.existsByAppointmentIdAndNotificationTypeAndStatus(
                appointment.getId(), NotificationType.APPOINTMENT_EXPIRED, NotificationStatus.FAILED);
        assertThat(failedLogged).isTrue();

        boolean sentLogged = notificationLogRepository.existsByAppointmentIdAndNotificationTypeAndStatus(
                appointment.getId(), NotificationType.APPOINTMENT_EXPIRED, NotificationStatus.SENT);
        assertThat(sentLogged).isFalse();

        notificationPort.shouldFail.set(false);
    }
}
