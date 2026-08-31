package com.randevu.backend.service;

import com.randevu.backend.entity.*;
import com.randevu.backend.exception.BusinessRuleException;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.repository.*;
import com.randevu.backend.service.AppointmentService.AppointmentAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// changeStatus, AppointmentService icinde gomulu bir durum makinesi -- ayri bir
// sinifa cikarilmadigi icin Spring context/DB kurmadan test etmenin yolu
// Mockito ile repository'leri sahtelemek. @InjectMocks constructor injection'i
// kullanir, tek gercek nesne test edilen AppointmentService'in kendisi.
//
// OWNER_ID/CUSTOMER_ID/STRANGER_ID sabit tutuluyor ki her testte "kim kim"
// sorusuna tekrar tekrar bakmak gerekmesin.
@ExtendWith(MockitoExtension.class)
class AppointmentServiceStateMachineTest {

    private static final Long OWNER_ID = 1L;
    private static final Long CUSTOMER_ID = 2L;
    private static final Long STRANGER_ID = 3L;
    private static final Long APPOINTMENT_ID = 100L;

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private ServiceItemRepository serviceItemRepository;
    @Mock
    private WorkingHourRepository workingHourRepository;
    @Mock
    private BusinessClosureRepository businessClosureRepository;
    @Mock
    private AvailabilityCalculator availabilityCalculator;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private StaffWorkingHourRepository staffWorkingHourRepository;
    @Mock
    private AppointmentExpiryPolicy expiryPolicy;

    private AppointmentService appointmentService;

    @BeforeEach
    void setUp() {
        // Sabit bir Clock -- changeStatus'un kendisi clock kullanmiyor ama
        // constructor zorunlu tutuyor (completeElapsedAppointments/
        // expireStaleRequests testleri asagida bunu gercekten kullanacak).
        Clock clock = Clock.fixed(Instant.parse("2026-09-01T09:00:00Z"), ZoneId.of("Europe/Istanbul"));
        appointmentService = new AppointmentService(appointmentRepository, businessRepository,
                serviceItemRepository, workingHourRepository, businessClosureRepository,
                availabilityCalculator, staffRepository, staffWorkingHourRepository, expiryPolicy, clock);
    }

    private Appointment appointmentWithStatus(AppointmentStatus status) {
        User owner = User.builder().id(OWNER_ID).build();
        User customer = User.builder().id(CUSTOMER_ID).build();
        Business business = Business.builder().owner(owner).build();
        return Appointment.builder()
                .id(APPOINTMENT_ID)
                .status(status)
                .business(business)
                .customer(customer)
                .appointmentDate(LocalDateTime.of(2026, 9, 5, 10, 0))
                .createdAt(LocalDateTime.of(2026, 9, 1, 9, 0))
                .build();
    }

    // Basarili gecis bekleyen testler icin: hem bulunuyor hem kaydediliyor.
    private void stubFind(Appointment appointment) {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // Exception bekleyen testler icin: save() hicbir zaman cagrilmiyor --
    // onu da stub'lamak Mockito'nun strict-stubbing modunda "unnecessary
    // stubbing" hatasi verir (kullanilmayan bir kurulum, testin gercekte
    // neyi dogruladigini belirsizlestirir).
    private void stubFindOnly(Appointment appointment) {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
    }

    // --- APPROVE ---

    @Test
    @DisplayName("PENDING randevu, isletme sahibi tarafindan APPROVED yapilabilir")
    void approve_pendingBySahibi_APPROVED_olur() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.PENDING);
        stubFind(appointment);

        Appointment result = appointmentService.changeStatus(APPOINTMENT_ID, AppointmentAction.APPROVE, OWNER_ID);

        assertThat(result.getStatus()).isEqualTo(AppointmentStatus.APPROVED);
    }

    @Test
    @DisplayName("APPROVE: isletme sahibi disinda biri (musteri dahil) deneyemez")
    void approve_sahipDisindaBiri_AccessDenied() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.PENDING);
        stubFindOnly(appointment);

        assertThatThrownBy(() -> appointmentService.changeStatus(APPOINTMENT_ID, AppointmentAction.APPROVE, CUSTOMER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("APPROVE: PENDING disindaki hicbir durumdan yapilamaz")
    void approve_pendingDisindaHerDurumdan_BusinessRule(AppointmentStatus mevcutDurum) {
        Appointment appointment = appointmentWithStatus(mevcutDurum);
        stubFindOnly(appointment);

        assertThatThrownBy(() -> appointmentService.changeStatus(APPOINTMENT_ID, AppointmentAction.APPROVE, OWNER_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("EXPIRED bir talebe islem denemesi ozel mesaj verir (genel mesaj degil)")
    void expiredRandevu_ozelMesajVerir() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.EXPIRED);
        stubFindOnly(appointment);

        assertThatThrownBy(() -> appointmentService.changeStatus(APPOINTMENT_ID, AppointmentAction.APPROVE, OWNER_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("zaman aşımına uğradı");
    }

    // --- REJECT ---

    @Test
    @DisplayName("PENDING randevu, isletme sahibi tarafindan REJECTED yapilabilir")
    void reject_pendingBySahibi_REJECTED_olur() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.PENDING);
        stubFind(appointment);

        Appointment result = appointmentService.changeStatus(APPOINTMENT_ID, AppointmentAction.REJECT, OWNER_ID);

        assertThat(result.getStatus()).isEqualTo(AppointmentStatus.REJECTED);
    }

    @Test
    @DisplayName("REJECT: musteri kendi randevusunu reddedemez")
    void reject_musteriDeneyemez_AccessDenied() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.PENDING);
        stubFindOnly(appointment);

        assertThatThrownBy(() -> appointmentService.changeStatus(APPOINTMENT_ID, AppointmentAction.REJECT, CUSTOMER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("REJECT: zaten APPROVED olan bir randevu reddedilemez")
    void reject_approvedRandevu_BusinessRule() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.APPROVED);
        stubFindOnly(appointment);

        assertThatThrownBy(() -> appointmentService.changeStatus(APPOINTMENT_ID, AppointmentAction.REJECT, OWNER_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    // --- CANCEL ---

    @Test
    @DisplayName("CANCEL: PENDING randevuyu musteri iptal edebilir")
    void cancel_pendingRandevuyuMusteriIptalEder() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.PENDING);
        stubFind(appointment);

        Appointment result = appointmentService.changeStatus(APPOINTMENT_ID, AppointmentAction.CANCEL, CUSTOMER_ID);

        assertThat(result.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    @DisplayName("CANCEL: APPROVED randevuyu isletme sahibi de iptal edebilir")
    void cancel_approvedRandevuyuSahipIptalEder() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.APPROVED);
        stubFind(appointment);

        Appointment result = appointmentService.changeStatus(APPOINTMENT_ID, AppointmentAction.CANCEL, OWNER_ID);

        assertThat(result.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    @DisplayName("CANCEL: ne musteri ne sahip olan biri iptal edemez")
    void cancel_yabanciBiri_AccessDenied() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.PENDING);
        stubFindOnly(appointment);

        assertThatThrownBy(() -> appointmentService.changeStatus(APPOINTMENT_ID, AppointmentAction.CANCEL, STRANGER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"PENDING", "APPROVED"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("CANCEL: terminal durumlardan (REJECTED/CANCELLED/COMPLETED/NO_SHOW/EXPIRED) iptal edilemez")
    void cancel_terminalDurumdan_BusinessRule(AppointmentStatus mevcutDurum) {
        Appointment appointment = appointmentWithStatus(mevcutDurum);
        stubFindOnly(appointment);

        assertThatThrownBy(() -> appointmentService.changeStatus(APPOINTMENT_ID, AppointmentAction.CANCEL, OWNER_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    // --- NO_SHOW ---

    @Test
    @DisplayName("NO_SHOW: APPROVED randevuyu sadece isletme sahibi isaretleyebilir")
    void noShow_approvedRandevuyuSahipIsaretler() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.APPROVED);
        stubFind(appointment);

        Appointment result = appointmentService.changeStatus(APPOINTMENT_ID, AppointmentAction.NO_SHOW, OWNER_ID);

        assertThat(result.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
    }

    @Test
    @DisplayName("NO_SHOW: musteri kendi kendine 'gelmedim' diyemez")
    void noShow_musteriKendiniIsaretleyemez_AccessDenied() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.APPROVED);
        stubFindOnly(appointment);

        assertThatThrownBy(() -> appointmentService.changeStatus(APPOINTMENT_ID, AppointmentAction.NO_SHOW, CUSTOMER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("NO_SHOW: henuz onaylanmamis (PENDING) randevuya musteri gelmedi denemez")
    void noShow_pendingRandevu_BusinessRule() {
        Appointment appointment = appointmentWithStatus(AppointmentStatus.PENDING);
        stubFindOnly(appointment);

        assertThatThrownBy(() -> appointmentService.changeStatus(APPOINTMENT_ID, AppointmentAction.NO_SHOW, OWNER_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    // --- Genel ---

    @Test
    @DisplayName("Var olmayan randevu ID'sine islem -> ResourceNotFoundException")
    void olmayanRandevu_ResourceNotFound() {
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.changeStatus(APPOINTMENT_ID, AppointmentAction.APPROVE, OWNER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("AppointmentAction.from: buyuk/kucuk harf farketmez, gecersiz deger BusinessRuleException firlatir")
    void action_from_parseKurallari() {
        assertThat(AppointmentAction.from("approve")).isEqualTo(AppointmentAction.APPROVE);
        assertThat(AppointmentAction.from("CaNcEl")).isEqualTo(AppointmentAction.CANCEL);
        assertThatThrownBy(() -> AppointmentAction.from("complete"))
                .isInstanceOf(BusinessRuleException.class);
    }

    // --- Zamanlanmis gecisler (kullanicidan degil, Clock'tan tetiklenir) ---

    @Test
    @DisplayName("completeElapsedAppointments: hizmet suresi + randevu saati gecmisse COMPLETED olur")
    void completeElapsedAppointments_gecmisRandevuTamamlanir() {
        ServiceItem otuzDakika = ServiceItem.builder().durationInMinutes(30).build();
        Appointment gecmis = Appointment.builder()
                .status(AppointmentStatus.APPROVED)
                .serviceItem(otuzDakika)
                // Clock 2026-09-01T09:00 Europe/Istanbul (UTC+3) -> yerel 12:00.
                // Randevu 11:00 + 30 dk = 11:30, "simdi"den (12:00) once bitmis.
                .appointmentDate(LocalDateTime.of(2026, 9, 1, 11, 0))
                .build();
        Appointment gelecek = Appointment.builder()
                .status(AppointmentStatus.APPROVED)
                .serviceItem(otuzDakika)
                .appointmentDate(LocalDateTime.of(2026, 9, 1, 13, 0))
                .build();
        when(appointmentRepository.findByStatus(AppointmentStatus.APPROVED)).thenReturn(List.of(gecmis, gelecek));

        int count = appointmentService.completeElapsedAppointments();

        assertThat(count).isEqualTo(1);
        assertThat(gecmis.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(gelecek.getStatus()).isEqualTo(AppointmentStatus.APPROVED);
        verify(appointmentRepository).saveAll(List.of(gecmis));
    }

    @Test
    @DisplayName("expireStaleRequests: karar tamamen AppointmentExpiryPolicy'ye devredilir")
    void expireStaleRequests_policyKararinaGoreDuser() {
        // Iki randevuya FARKLI randevu saatleri verildi: appointmentWithStatus()
        // ikisine de ayni sabit tarihi verseydi, Mockito eq(...) eslesmesi
        // ikisini de AYNI stub olarak gorur (LocalDateTime.equals() degere
        // bakar, kimlige degil) -- son kayitli stub ikisine de uygulanirdi.
        Appointment dusecek = appointmentWithStatus(AppointmentStatus.PENDING);
        Appointment dusmeyecek = appointmentWithStatus(AppointmentStatus.PENDING);
        dusmeyecek.setAppointmentDate(dusecek.getAppointmentDate().plusDays(1));
        when(appointmentRepository.findByStatus(AppointmentStatus.PENDING)).thenReturn(List.of(dusecek, dusmeyecek));
        when(expiryPolicy.isExpired(eq(dusecek.getCreatedAt()), eq(dusecek.getAppointmentDate()), any()))
                .thenReturn(true);
        when(expiryPolicy.isExpired(eq(dusmeyecek.getCreatedAt()), eq(dusmeyecek.getAppointmentDate()), any()))
                .thenReturn(false);

        int count = appointmentService.expireStaleRequests();

        assertThat(count).isEqualTo(1);
        assertThat(dusecek.getStatus()).isEqualTo(AppointmentStatus.EXPIRED);
        assertThat(dusmeyecek.getStatus()).isEqualTo(AppointmentStatus.PENDING);
    }
}
