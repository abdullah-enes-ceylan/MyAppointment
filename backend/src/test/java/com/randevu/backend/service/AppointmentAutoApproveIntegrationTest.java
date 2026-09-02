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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Isletme basina "otomatik onay" anahtari (bkz. CLAUDE.md karar tablosu,
// "Randevu istek mi, direkt mi"). Bu test SADECE bu anahtarin
// AppointmentService.createAppointment'taki baslangic-durumu secimini
// dogru etkiledigini kanitliyor -- createAppointment'in geri kalan
// dogrulamalari (cakisma, ufuk, acik talep siniri) BASKA bir yerde ayrica
// test edilmiyor (bu proje o metot icin ozel bir birim testi hic yazmamis),
// o yuzden burada SADECE yeni davranisi ekliyoruz, mevcut bir bosluğu
// doldurmaya calismiyoruz.
class AppointmentAutoApproveIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AppointmentService appointmentService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ServiceItemRepository serviceItemRepository;

    private User owner;
    private User customer;
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
    }

    private Business saveBusiness(boolean autoApprove) {
        return businessRepository.save(Business.builder()
                .name("Test İşletme").address("Test Adres").owner(owner)
                .category(BusinessCategory.HAIRDRESSER).autoApprove(autoApprove)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0))
                .build());
    }

    private ServiceItem saveServiceFor(Business business) {
        return serviceItemRepository.save(ServiceItem.builder()
                .name("Saç Kesimi").description("Test").price(BigDecimal.valueOf(100))
                .durationInMinutes(30).business(business).build());
    }

    private Appointment newAppointmentRequest(Business business, ServiceItem service, LocalDateTime date) {
        return Appointment.builder()
                .business(Business.builder().id(business.getId()).build())
                .serviceItem(ServiceItem.builder().id(service.getId()).build())
                .customer(customer)
                .appointmentDate(date)
                .build();
    }

    @Test
    @DisplayName("autoApprove=false (varsayılan): yeni talep PENDING doğar")
    void autoApproveKapaliysa_talepPendingDogar() {
        Business business = saveBusiness(false);
        ServiceItem service = saveServiceFor(business);

        Appointment created = appointmentService.createAppointment(
                newAppointmentRequest(business, service, LocalDateTime.now().plusDays(2).withHour(12).withMinute(0).withSecond(0).withNano(0)));

        assertThat(created.getStatus()).isEqualTo(AppointmentStatus.PENDING);
    }

    @Test
    @DisplayName("autoApprove=true: yeni talep İstek Kutusu'nu hiç görmeden doğrudan APPROVED doğar")
    void autoApproveAcikken_talepDogrudanApprovedDogar() {
        Business business = saveBusiness(true);
        ServiceItem service = saveServiceFor(business);

        Appointment created = appointmentService.createAppointment(
                newAppointmentRequest(business, service, LocalDateTime.now().plusDays(2).withHour(13).withMinute(0).withSecond(0).withNano(0)));

        assertThat(created.getStatus()).isEqualTo(AppointmentStatus.APPROVED);
    }

    @Test
    @DisplayName("autoApprove=true olsa da aynı saate ikinci talep çakışma kontrolüne takılır")
    void autoApproveAcikkenBile_cakismaKontroluAtlanmaz() {
        Business business = saveBusiness(true);
        ServiceItem service = saveServiceFor(business);
        LocalDateTime slot = LocalDateTime.now().plusDays(3).withHour(14).withMinute(0).withSecond(0).withNano(0);

        appointmentService.createAppointment(newAppointmentRequest(business, service, slot));

        assertThatThrownBy(() -> appointmentService.createAppointment(newAppointmentRequest(business, service, slot)))
                .isInstanceOf(BusinessRuleException.class);
    }
}
