package com.randevu.backend.service;

import com.randevu.backend.AbstractIntegrationTest;
import com.randevu.backend.entity.Appointment;
import com.randevu.backend.entity.AppointmentStatus;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.BusinessCategory;
import com.randevu.backend.entity.Role;
import com.randevu.backend.entity.ServiceItem;
import com.randevu.backend.entity.User;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.ServiceItemRepository;
import com.randevu.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

// CLAUDE.md "Bilinen açık işler"nde tespit edilen tutarsızlığın regresyon
// testi: /me/upcoming (getUpcomingCustomerAppointments) ve profil özeti
// (ProfileStatsService.upcomingAppointments) daha önce FARKLI kriterler
// kullanıyordu -- biri sadece tarihe, diğeri AppointmentStatus.ACTIVE_STATUSES'a
// bakıyordu. Gelecek tarihli bir CANCELLED randevu ilkinde görünüp
// ikincisinde görünmüyordu. Düzeltme sonrası üçü de (müşteri listesi,
// işletme listesi, profil sayacı) AYNI kaynaktan (ACTIVE_STATUSES) okuyor.
class UpcomingAppointmentsConsistencyTest extends AbstractIntegrationTest {

    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private ProfileStatsService profileStatsService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ServiceItemRepository serviceItemRepository;

    private User owner;
    private User customer;
    private Business business;
    private ServiceItem serviceItem;

    @BeforeEach
    void setUp() {
        String unique = String.valueOf(System.nanoTime());
        owner = userRepository.save(User.builder()
                .name("Sahip").surName("Test").email("owner-" + unique + "@test.com")
                .password("x").phone("0500").role(Role.BUSINESS_OWNER).build());
        customer = userRepository.save(User.builder()
                .name("Musteri").surName("Test").email("customer-" + unique + "@test.com")
                .password("x").phone("0500").role(Role.USER).build());
        business = businessRepository.save(Business.builder()
                .name("Test İşletme").address("Test Adres").owner(owner)
                .category(BusinessCategory.HAIRDRESSER).autoApprove(true)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0))
                .build());
        serviceItem = serviceItemRepository.save(ServiceItem.builder()
                .name("Saç Kesimi").description("Test").price(BigDecimal.valueOf(100))
                .durationInMinutes(30).business(business).build());
    }

    private Appointment newAppointmentRequest(LocalDateTime date) {
        return Appointment.builder()
                .business(Business.builder().id(business.getId()).build())
                .serviceItem(ServiceItem.builder().id(serviceItem.getId()).build())
                .customer(customer)
                .appointmentDate(date)
                .build();
    }

    @Test
    @DisplayName("gelecek tarihli CANCELLED randevu ne musteri/isletme listesinde ne profil sayacinda gorunur")
    void iptalEdilmisGelecekRandevu_hicbirYerdeYaklasanSayilmaz() {
        LocalDateTime approvedSlot = LocalDateTime.now().plusDays(2).withHour(12).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime cancelledSlot = LocalDateTime.now().plusDays(2).withHour(13).withMinute(0).withSecond(0).withNano(0);

        Appointment approved = appointmentService.createAppointment(newAppointmentRequest(approvedSlot));
        Appointment toCancel = appointmentService.createAppointment(newAppointmentRequest(cancelledSlot));
        assertThat(approved.getStatus()).isEqualTo(AppointmentStatus.APPROVED);

        appointmentService.changeStatus(toCancel.getId(), AppointmentService.AppointmentAction.CANCEL, customer.getId());

        assertThat(appointmentService.getUpcomingCustomerAppointments(customer.getId()))
                .extracting(Appointment::getId)
                .containsExactly(approved.getId());

        assertThat(appointmentService.getUpcomingBusinessAppointments(business.getId()))
                .extracting(Appointment::getId)
                .containsExactly(approved.getId());

        assertThat(profileStatsService.getStatsForUser(customer.getId()).upcomingAppointments())
                .isEqualTo(1);
    }
}
