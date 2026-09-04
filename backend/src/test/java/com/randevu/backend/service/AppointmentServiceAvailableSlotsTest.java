package com.randevu.backend.service;

import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.ServiceItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// getAvailableTimeSlots'un "bugün" için geçmiş (ve minimumBookingLeadTime
// payından daha yakın) saatleri elediğini kanıtlıyor. AppointmentServiceStateMachineTest'teki
// AYNI Mockito deseni (repository'ler sahte, sabit Clock enjekte) -- bu
// metodun kendisi Clock kullanmıyor gibi görünse de artık kullanıyor, o
// yüzden testte de gerçek saat KARIŞAMAZ (bkz. o dosyadaki sabit-Clock
// gerekçesi, aynı sınıf flaky-test riskinden kaçınmak için).
//
// availabilityCalculator BİLEREK sahte (gerçek algoritma değil) -- amaç
// AvailabilityCalculator'ın kendisini yeniden test etmek değil (o zaten
// AvailabilityCalculatorTest'te var), sadece getAvailableTimeSlots'un
// calculate()'ten dönen ham listeyi "bugün" için doğru filtrelediğini
// göstermek.
@ExtendWith(MockitoExtension.class)
class AppointmentServiceAvailableSlotsTest {

    private static final Long BUSINESS_ID = 1L;
    private static final Long SERVICE_ID = 2L;

    @Mock
    private com.randevu.backend.repository.AppointmentRepository appointmentRepository;
    @Mock
    private com.randevu.backend.repository.BusinessRepository businessRepository;
    @Mock
    private com.randevu.backend.repository.ServiceItemRepository serviceItemRepository;
    @Mock
    private com.randevu.backend.repository.WorkingHourRepository workingHourRepository;
    @Mock
    private com.randevu.backend.repository.BusinessClosureRepository businessClosureRepository;
    @Mock
    private AvailabilityCalculator availabilityCalculator;
    @Mock
    private com.randevu.backend.repository.StaffRepository staffRepository;
    @Mock
    private com.randevu.backend.repository.StaffWorkingHourRepository staffWorkingHourRepository;
    @Mock
    private AppointmentExpiryPolicy expiryPolicy;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    // 2026-09-04 14:00:00 Europe/Istanbul -- ogleden sonra, sabah
    // saatlerinin kesinlikle "gecmis" sayilmasi gereken bir an.
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 4);
    private static final Clock FIXED_AFTERNOON_CLOCK =
            Clock.fixed(Instant.parse("2026-09-04T11:00:00Z"), ZoneId.of("Europe/Istanbul"));

    private AppointmentService appointmentService;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(appointmentRepository, businessRepository,
                serviceItemRepository, workingHourRepository, businessClosureRepository,
                availabilityCalculator, staffRepository, staffWorkingHourRepository, expiryPolicy,
                FIXED_AFTERNOON_CLOCK, eventPublisher);

        Business business = Business.builder().id(BUSINESS_ID)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(20, 0)).build();
        ServiceItem service = ServiceItem.builder().id(SERVICE_ID).durationInMinutes(30).build();

        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business));
        when(serviceItemRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        // Personelsiz yol: hicbir aktif personel yok -> eski (isletme
        // capinda) davranisa duser.
        when(staffRepository.findByBusinessIdAndIsActiveTrue(BUSINESS_ID)).thenReturn(List.of());
        when(businessClosureRepository.findByBusinessIdAndDate(eq(BUSINESS_ID), any())).thenReturn(Optional.empty());
        when(workingHourRepository.findByBusinessIdAndDayOfWeek(eq(BUSINESS_ID), any())).thenReturn(Optional.empty());
        when(appointmentRepository.findByBusinessIdAndAppointmentDateBetweenAndStatusIn(
                eq(BUSINESS_ID), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Bugün için: saat 14:00 + 15dk paydan önceki saatler dönmez, sonrakiler döner")
    void bugunIcin_gecmisVeCokYakinSlotlarElenir() {
        List<LocalTime> rawSlots = List.of(
                LocalTime.of(9, 0),    // sabah -- acikca gecmis
                LocalTime.of(13, 45),  // "su an"dan (14:00) once -- gecmis
                LocalTime.of(14, 0),   // tam "su an" -- pay icinde, hala elenir
                LocalTime.of(14, 15),  // tam kesim noktasi (14:00 + 15dk) -- kalir
                LocalTime.of(17, 0)    // acikca gelecek -- kalir
        );
        when(availabilityCalculator.calculate(eq(TODAY), any(), any(), anyInt(), any())).thenReturn(rawSlots);
        when(expiryPolicy.getMinimumBookingLeadTime()).thenReturn(Duration.ofMinutes(15));

        List<LocalTime> result = appointmentService.getAvailableTimeSlots(BUSINESS_ID, SERVICE_ID, TODAY);

        assertThat(result).containsExactly(LocalTime.of(14, 15), LocalTime.of(17, 0));
    }

    @Test
    @DisplayName("Gelecek bir tarih için: hiçbir filtre uygulanmaz, ham liste aynen döner")
    void gelecekTarihIcin_filtrelemeUygulanmaz() {
        LocalDate futureDate = TODAY.plusDays(3);
        List<LocalTime> rawSlots = List.of(LocalTime.of(9, 0), LocalTime.of(9, 15), LocalTime.of(19, 45));
        when(availabilityCalculator.calculate(eq(futureDate), any(), any(), anyInt(), any())).thenReturn(rawSlots);

        List<LocalTime> result = appointmentService.getAvailableTimeSlots(BUSINESS_ID, SERVICE_ID, futureDate);

        assertThat(result).containsExactlyElementsOf(rawSlots);
    }
}
