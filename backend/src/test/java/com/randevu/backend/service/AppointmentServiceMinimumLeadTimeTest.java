package com.randevu.backend.service;

import com.randevu.backend.AbstractIntegrationTest;
import com.randevu.backend.entity.Appointment;
import com.randevu.backend.entity.AppointmentStatus;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.BusinessCategory;
import com.randevu.backend.entity.Role;
import com.randevu.backend.entity.ServiceItem;
import com.randevu.backend.entity.User;
import com.randevu.backend.exception.BusinessRuleException;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.ServiceItemRepository;
import com.randevu.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// createAppointment'a eklenen "cok yakinda" kontrolunu kanitliyor --
// getAvailableTimeSlots'un "bugun" icin uyguladigi filtreyle (bkz.
// AppointmentServiceAvailableSlotsTest) AYNI esik (expiryPolicy.getMinimumBookingLeadTime(),
// tek kaynak AppointmentPolicyProperties), simdi olusturma tarafinda da.
// AccountDeletionResponseFieldsTest'teki AYNI desen: @TestConfiguration ile
// @Primary sabit Clock -- @Future (DTO seviyesi) burada devrede DEGIL cunku
// servis DOGRUDAN cagriliyor (controller/bean validation atlanIYOR,
// AppointmentAutoApproveIntegrationTest'teki ayni yaklasim), yani bu testte
// GORULEN reddi SADECE bizim yeni kontrolumuz uretiyor.
class AppointmentServiceMinimumLeadTimeTest extends AbstractIntegrationTest {

    // 2026-09-04T14:00:00 Europe/Istanbul (=11:00Z, UTC+3) --
    // AppointmentServiceAvailableSlotsTest'teki AYNI sabit an, iki testin
    // ayni "su an"a gore ayni sinirlari dogrulamasi icin bilerek ortak.
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-09-04T11:00:00Z"), ZoneId.of("Europe/Istanbul"));
        }
    }

    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ServiceItemRepository serviceItemRepository;

    private User customer;
    private ServiceItem serviceItem;

    @BeforeEach
    void setUp() {
        String unique = String.valueOf(System.nanoTime());
        User owner = userRepository.save(User.builder()
                .name("Sahip").surName("Test").email("owner-lead-" + unique + "@test.com")
                .password("x").phone("0500").role(Role.BUSINESS_OWNER).build());
        customer = userRepository.save(User.builder()
                .name("Musteri").surName("Test").email("customer-lead-" + unique + "@test.com")
                .password("x").phone("0500").role(Role.USER).build());
        Business business = businessRepository.save(Business.builder()
                .name("Test İşletme").address("Test Adres").owner(owner)
                .category(BusinessCategory.HAIRDRESSER)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(20, 0))
                .build());
        serviceItem = serviceItemRepository.save(ServiceItem.builder()
                .name("Saç Kesimi").description("Test").price(BigDecimal.valueOf(100))
                .durationInMinutes(30).business(business).build());
    }

    private Appointment newAppointmentRequest(LocalDateTime date) {
        return Appointment.builder()
                .business(Business.builder().id(serviceItem.getBusiness().getId()).build())
                .serviceItem(ServiceItem.builder().id(serviceItem.getId()).build())
                .customer(customer)
                .appointmentDate(date)
                .build();
    }

    @Test
    @DisplayName("Pay penceresi İÇİNDEKİ bir saat (şu an + 10dk) reddedilir, mesaj eşik dakikasını açıkça söyler")
    void payPenceresiIcindekiSaat_reddedilirVeMesajAcik() {
        LocalDateTime tooSoon = LocalDateTime.of(2026, 9, 4, 14, 10, 0);

        assertThatThrownBy(() -> appointmentService.createAppointment(newAppointmentRequest(tooSoon)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("en az 15 dakika sonrası");
    }

    @Test
    @DisplayName("Pay penceresinin tam sınırındaki saat (şu an + 15dk) kabul edilir")
    void payPenceresininHemenDisindakiSaat_kabulEdilir() {
        LocalDateTime rightAtEdge = LocalDateTime.of(2026, 9, 4, 14, 15, 0);

        Appointment created = appointmentService.createAppointment(newAppointmentRequest(rightAtEdge));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(AppointmentStatus.PENDING);
    }
}
