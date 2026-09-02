package com.randevu.backend.service;

import com.randevu.backend.AbstractIntegrationTest;
import com.randevu.backend.entity.Appointment;
import com.randevu.backend.entity.AppointmentStatus;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.BusinessCategory;
import com.randevu.backend.entity.Favorite;
import com.randevu.backend.entity.NotificationStatus;
import com.randevu.backend.entity.NotificationType;
import com.randevu.backend.entity.Role;
import com.randevu.backend.entity.ServiceItem;
import com.randevu.backend.entity.User;
import com.randevu.backend.exception.BusinessRuleException;
import com.randevu.backend.repository.AppointmentRepository;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.FavoriteRepository;
import com.randevu.backend.repository.NotificationLogRepository;
import com.randevu.backend.repository.ServiceItemRepository;
import com.randevu.backend.repository.UserRepository;
import com.randevu.backend.scheduler.AccountDeletionScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Faz 3.9 -- hesap silme akisinin dogrulugu ANCAK zamani manipule ederek
// kanitlanabilir (48 saat/30 gun gercekten beklenemez). Kullanicinin
// istedigi bes senaryonun birebir karsiligi asagida, gercek Postgres
// uzerinde (AbstractIntegrationTest) -- InMemoryRateLimiterTest'teki
// "saati elle ilerletilebilen Clock" deseni burada da kullaniliyor, ama
// bu sefer TUM Spring context'inin paylastigi TEK @Primary bean olarak:
// AppointmentService, NotificationService, AccountDeletionService/Scheduler
// hepsi AYNI saatten okuyor, tıpki üretimde TimeConfig'in Clock bean'inden
// okudugu gibi.
class AccountDeletionIntegrationTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        Clock mutableClock() {
            return new MutableClock(Instant.parse("2026-09-01T10:00:00Z"));
        }
    }

    static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceBy(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("Test yardımcısı -- zone değiştirmeye gerek yok");
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Autowired
    private AccountDeletionService accountDeletionService;
    @Autowired
    private AccountDeletionScheduler accountDeletionScheduler;
    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ServiceItemRepository serviceItemRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private FavoriteRepository favoriteRepository;
    @Autowired
    private NotificationLogRepository notificationLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MutableClock clock;

    private static final String RAW_PASSWORD = "Sifre1234!";

    private User owner;
    private User customer;
    private Business business;
    private ServiceItem serviceItem;

    @BeforeEach
    void setUp() {
        String unique = String.valueOf(System.nanoTime());
        owner = userRepository.save(User.builder()
                .name("Sahip").surName("Test").email("owner-" + unique + "@test.com")
                .password(passwordEncoder.encode(RAW_PASSWORD)).phone("0500").role(Role.BUSINESS_OWNER).build());
        customer = userRepository.save(User.builder()
                .name("Müşteri").surName("Test").email("customer-" + unique + "@test.com")
                .password(passwordEncoder.encode(RAW_PASSWORD)).phone("0500").role(Role.USER).build());
        business = businessRepository.save(Business.builder()
                .name("Test İşletme " + unique).address("Test Adres").owner(owner)
                .category(BusinessCategory.HAIRDRESSER).build());
        serviceItem = serviceItemRepository.save(ServiceItem.builder()
                .name("Saç Kesimi").description("Test").price(BigDecimal.valueOf(100))
                .durationInMinutes(30).business(business).build());
    }

    private Appointment createAppointment(LocalDateTime appointmentDate, AppointmentStatus status) {
        Appointment appointment = Appointment.builder()
                .status(status)
                .business(business)
                .customer(customer)
                .serviceItem(serviceItem)
                .appointmentDate(appointmentDate)
                .createdAt(LocalDateTime.now(clock))
                .build();
        return appointmentRepository.save(appointment);
    }

    private User reloadOwner() {
        return userRepository.findById(owner.getId()).orElseThrow();
    }

    private Business reloadBusiness() {
        return businessRepository.findById(business.getId()).orElseThrow();
    }

    private Appointment reloadAppointment(Appointment appointment) {
        return appointmentRepository.findById(appointment.getId()).orElseThrow();
    }

    // Tasarımın asıl amacı iptalin KENDİSİ değil, iptal edilen randevunun
    // müşterisine haber gitmesi -- iptal çalışıp bildirim sessizce
    // başarısız olursa müşteri neden randevusunun gittiğini hiç öğrenmez.
    // NotificationLogRepository'deki SENT kaydı, sahte bir port değil
    // GERÇEK InAppNotificationAdapter üzerinden gittiğinin kanıtı (bkz.
    // AppointmentExpiryNotificationIntegrationTest'teki aynı desen).
    private void assertCancelledNotificationSent(Appointment appointment) {
        boolean sent = notificationLogRepository.existsByAppointmentIdAndNotificationTypeAndStatus(
                appointment.getId(), NotificationType.APPOINTMENT_CANCELLED_BUSINESS_CLOSED, NotificationStatus.SENT);
        assertThat(sent)
                .withFailMessage("Randevu %d iptal edildi ama müşteriye APPOINTMENT_CANCELLED_BUSINESS_CLOSED "
                        + "bildirimi SENT olarak loglanmadı.", appointment.getId())
                .isTrue();
    }

    private void assertNoCancelledNotificationSent(Appointment appointment) {
        boolean sent = notificationLogRepository.existsByAppointmentIdAndNotificationTypeAndStatus(
                appointment.getId(), NotificationType.APPOINTMENT_CANCELLED_BUSINESS_CLOSED, NotificationStatus.SENT);
        assertThat(sent).isFalse();
    }

    // Senaryo 1: "Talep verildi, 47. saatte geri alındı → hiçbir randevu
    // iptal olmadı mı, hesap normale döndü mü". Randevu bilerek haber
    // verme payinin (72s) DISINDA (80 saat sonra) -- bu senaryonun konusu
    // haber verme payi degil, geri donus penceresi (48s).
    @Test
    @DisplayName("47. saatte geri alma: hiçbir randevu iptal olmadı, hesap tamamen normale döndü")
    void talep47SaatteGeriAlinirsa_hicbirRandevuIptalOlmazVeHesapNormaleDoner() {
        Appointment farAppointment = createAppointment(LocalDateTime.now(clock).plusHours(80), AppointmentStatus.APPROVED);

        accountDeletionService.requestDeletion(owner, RAW_PASSWORD);
        assertThat(reloadBusiness().getSuspendedAt()).isNotNull();

        clock.advanceBy(Duration.ofHours(47));

        accountDeletionService.cancelDeletion(reloadOwner());

        assertThat(reloadOwner().getDeletionRequestedAt()).isNull();
        assertThat(reloadBusiness().getSuspendedAt()).isNull();
        assertThat(reloadAppointment(farAppointment).getStatus()).isEqualTo(AppointmentStatus.APPROVED);
        assertNoCancelledNotificationSent(farAppointment);

        // Geri alindiktan SONRA scheduler calissa bile artik hicbir sey
        // yapmamali -- talep zaten yok.
        accountDeletionScheduler.bulkCancelAfterReversalWindow();
        assertThat(reloadAppointment(farAppointment).getStatus()).isEqualTo(AppointmentStatus.APPROVED);
        assertNoCancelledNotificationSent(farAppointment);
    }

    // Senaryo 2: "Talep verildi, 49. saatte scheduler çalıştı → tüm
    // gelecek randevular iptal mi". Randevu yine haber verme payinin
    // disinda -- talep aninda degil, SADECE geri donus penceresi (48s)
    // dolunca iptal edildigini kanitlamak icin.
    @Test
    @DisplayName("49. saatte scheduler: geri dönüş penceresi dolduğu için kalan tüm randevular iptal edildi")
    void talep49SaatteSchedulerCalisirsa_tumGelecekRandevularIptalEdilir() {
        Appointment farAppointment = createAppointment(LocalDateTime.now(clock).plusHours(80), AppointmentStatus.APPROVED);

        accountDeletionService.requestDeletion(owner, RAW_PASSWORD);
        // Talep aninda henuz dokunulmadi -- 80 saat, 72 saatlik haber verme
        // payinin disinda.
        assertThat(reloadAppointment(farAppointment).getStatus()).isEqualTo(AppointmentStatus.APPROVED);

        clock.advanceBy(Duration.ofHours(49));
        accountDeletionScheduler.bulkCancelAfterReversalWindow();

        assertThat(reloadAppointment(farAppointment).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertCancelledNotificationSent(farAppointment);
    }

    // Senaryo 3: "Talep anında 72 saat içindeki randevular iptal, 73.
    // saatteki randevu dokunulmadan duruyor mu".
    @Test
    @DisplayName("Talep anında: 72 saat içindekiler hemen iptal, 73. saatteki dokunulmadan kalır")
    void talepAninda_72SaatIcindekilerIptalEdilir73SaattekineDokunulmaz() {
        Appointment within72h = createAppointment(LocalDateTime.now(clock).plusHours(71), AppointmentStatus.APPROVED);
        Appointment beyond72h = createAppointment(LocalDateTime.now(clock).plusHours(73), AppointmentStatus.APPROVED);

        accountDeletionService.requestDeletion(owner, RAW_PASSWORD);

        assertThat(reloadAppointment(within72h).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertCancelledNotificationSent(within72h);
        assertThat(reloadAppointment(beyond72h).getStatus()).isEqualTo(AppointmentStatus.APPROVED);
        assertNoCancelledNotificationSent(beyond72h);
    }

    // Senaryo 4: "Askıdaki işletmeye yeni randevu denemesi reddediliyor mu".
    @Test
    @DisplayName("Askıdaki işletmeye yeni randevu denemesi reddedilir")
    void askidakiIsletmeyeYeniRandevuDenemesiReddedilir() {
        accountDeletionService.requestDeletion(owner, RAW_PASSWORD);

        Appointment attempt = Appointment.builder()
                .status(AppointmentStatus.PENDING)
                .business(business)
                .customer(customer)
                .serviceItem(serviceItem)
                .appointmentDate(LocalDateTime.now(clock).plusDays(1).withHour(12).withMinute(0).withSecond(0).withNano(0))
                .build();

        assertThatThrownBy(() -> appointmentService.createAppointment(attempt))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("randevu kabul etmiyor");
    }

    // Senaryo 5: "30. günde anonimleştirme çalıştı, randevu satırları
    // duruyor ama kişisel alanlar temiz mi". USER (musteri) rolu uzerinden
    // test ediliyor: musteri kendi hesabini siler, 30 gun sonra kendi
    // cevaplanmamis/onaylanmis randevusu iptal edilir (satir SILINMEZ,
    // sadece durumu degisir), favorisi HARD DELETE edilir, kimlik bilgileri
    // geri donusturulemez sekilde temizlenir.
    @Test
    @DisplayName("30. günde anonimleştirme: randevu satırı duruyor (CANCELLED), kişisel alanlar temizlendi, favori silindi")
    void gunOtuzdaAnonimlestirme_randevuSatiriDururKisiselAlanlarTemizlenirFavoriSilinir() {
        Appointment ownAppointment = Appointment.builder()
                .status(AppointmentStatus.APPROVED)
                .business(business)
                .customer(customer)
                .serviceItem(serviceItem)
                .appointmentDate(LocalDateTime.now(clock).plusDays(5))
                .createdAt(LocalDateTime.now(clock))
                .build();
        ownAppointment = appointmentRepository.save(ownAppointment);

        favoriteRepository.save(Favorite.builder()
                .user(customer)
                .business(business)
                .createdAt(LocalDateTime.now(clock))
                .build());

        accountDeletionService.requestDeletion(customer, RAW_PASSWORD);

        clock.advanceBy(Duration.ofDays(30));
        accountDeletionScheduler.anonymizeAfterGracePeriod();

        User reloaded = userRepository.findById(customer.getId()).orElseThrow();
        assertThat(reloaded.getAnonymizedAt()).isNotNull();
        assertThat(reloaded.getName()).isNotEqualTo("Müşteri");
        assertThat(reloaded.getEmail()).contains("deleted-user-" + customer.getId());
        assertThat(reloaded.getPhone()).isEmpty();
        assertThat(passwordEncoder.matches(RAW_PASSWORD, reloaded.getPassword())).isFalse();

        // Randevu SATIRI duruyor -- silinmedi, sadece iptal edildi.
        Appointment reloadedAppointment = appointmentRepository.findById(ownAppointment.getId()).orElseThrow();
        assertThat(reloadedAppointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);

        assertThat(favoriteRepository.findByUser_IdOrderByCreatedAtDesc(customer.getId())).isEmpty();
    }

    // Ek soru: "DELETE /api/users/me idempotent mi?" DEĞİL -- BİLEREK.
    // İkinci çağrı deletionRequestedAt'i YENİDEN yazsaydı sayaç sıfırlanır,
    // haber verme payı (72s) ve geri dönüş penceresi (48s) baştan
    // başlardı -- ele geçirilmiş bir hesapta saldırgan periyodik olarak
    // tekrar tetikleyip süreci sonsuza dek erteleyebilirdi. Bu yüzden ikinci
    // istek REDDEDİLİYOR (409), ilk talebin zaman damgası DOKUNULMADAN kalıyor.
    @Test
    @DisplayName("İkinci silme talebi reddedilir, ilk talebin zaman damgası sıfırlanmaz")
    void ikinciSilmeTalebiReddedilirIlkZamanDamgasiKorunur() {
        accountDeletionService.requestDeletion(owner, RAW_PASSWORD);
        LocalDateTime firstRequestedAt = reloadOwner().getDeletionRequestedAt();

        clock.advanceBy(Duration.ofHours(10));

        assertThatThrownBy(() -> accountDeletionService.requestDeletion(reloadOwner(), RAW_PASSWORD))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("zaten mevcut");

        assertThat(reloadOwner().getDeletionRequestedAt()).isEqualTo(firstRequestedAt);
    }

    // Ek soru: "cancelDeletion anonimleştirilmiş bir hesapta çağrılırsa ne
    // olur?" Normal akışta bu satıra hiç ulaşılmaz (anonimlestirme sonrası
    // enabled=false, giriş yapılamaz) ama uç noktanın kendisi yine de
    // anlamlı bir hata vermeli, sessizce "başarılı" dönüp anonimleştirmeyi
    // GERİ ALIYORMUŞ gibi görünmemeli.
    @Test
    @DisplayName("Anonimleştirilmiş hesapta cancelDeletion reddedilir, anonim alanlar değişmez")
    void anonimlestirilmisHesaptaCancelDeletionReddedilir() {
        accountDeletionService.requestDeletion(customer, RAW_PASSWORD);
        clock.advanceBy(Duration.ofDays(30));
        accountDeletionScheduler.anonymizeAfterGracePeriod();

        User anonymized = userRepository.findById(customer.getId()).orElseThrow();
        assertThat(anonymized.getAnonymizedAt()).isNotNull();
        String anonymizedEmail = anonymized.getEmail();

        assertThatThrownBy(() -> accountDeletionService.cancelDeletion(anonymized))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("anonimleştirildi");

        User reloaded = userRepository.findById(customer.getId()).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo(anonymizedEmail);
        assertThat(reloaded.getAnonymizedAt()).isNotNull();
    }
}
