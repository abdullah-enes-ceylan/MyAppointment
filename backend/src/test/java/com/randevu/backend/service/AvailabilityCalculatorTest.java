package com.randevu.backend.service;

import com.randevu.backend.exception.BusinessRuleException;
import com.randevu.backend.service.AvailabilityCalculator.BusyInterval;
import com.randevu.backend.service.AvailabilityCalculator.SlotAssignment;
import com.randevu.backend.service.AvailabilityCalculator.StaffAvailability;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Spring context'e (Hibernate, DB) hic ihtiyac yok -- AvailabilityCalculator
// bilerek JPA'dan bagimsiz (bkz. sinif ustundeki aciklama). slotGranularityMinutes
// alani elle set edilmiyor (Spring @Value burada calismaz), int varsayilani
// 0 oldugu icin effectiveGranularity() zaten 15 dakikaya duser -- testler
// bunu varsayiyor.
class AvailabilityCalculatorTest {

    private final AvailabilityCalculator calculator = new AvailabilityCalculator();
    private final LocalDate date = LocalDate.of(2026, 9, 1); // bir Sali

    @Test
    void tekPersonel_mesguliyetYokken_tumIzgarayiDoner() {
        StaffAvailability staff = new StaffAvailability(1L, LocalTime.of(9, 0), LocalTime.of(10, 0), List.of());

        List<SlotAssignment> result = calculator.calculateForStaff(date, 30, List.of(staff));

        // 9:00-10:00 arasi, 30 dk hizmet, 15 dk izgara -> 9:00, 9:15, 9:30
        // baslangicli uc slot sigar (9:30 baslayan tam 10:00'da biter, sinira
        // esit oldugu icin hala gecerli -- while kosulu isAfter, isAfterOrEqual degil).
        assertThat(result).extracting(SlotAssignment::time)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(9, 15), LocalTime.of(9, 30));
        assertThat(result).allMatch(s -> s.assignedStaffId().equals(1L));
    }

    @Test
    void ikiPersonelinCalismaSaatleriCakismiyorsa_ikisininSlotlariDaDoner() {
        StaffAvailability sabahci = new StaffAvailability(1L, LocalTime.of(9, 0), LocalTime.of(12, 0), List.of());
        StaffAvailability oglenci = new StaffAvailability(2L, LocalTime.of(14, 0), LocalTime.of(18, 0), List.of());

        List<SlotAssignment> result = calculator.calculateForStaff(date, 60, List.of(sabahci, oglenci));

        // 12:00-14:00 arasindaki bosluk hic slot uretmemeli.
        assertThat(result).noneMatch(s -> s.time().isAfter(LocalTime.of(11, 0)) && s.time().isBefore(LocalTime.of(14, 0)));
        assertThat(result).anyMatch(s -> s.time().equals(LocalTime.of(9, 0)) && s.assignedStaffId().equals(1L));
        assertThat(result).anyMatch(s -> s.time().equals(LocalTime.of(14, 0)) && s.assignedStaffId().equals(2L));
    }

    @Test
    void birPersonelMesgulseDigeriMusaitseSlotYineDeDoner() {
        // 10:00-10:30 randevusu olan personel 1, bos olan personel 2.
        BusyInterval busy = new BusyInterval(
                LocalDateTime.of(date, LocalTime.of(10, 0)),
                LocalDateTime.of(date, LocalTime.of(10, 30)));
        StaffAvailability dolu = new StaffAvailability(1L, LocalTime.of(9, 0), LocalTime.of(12, 0), List.of(busy));
        StaffAvailability bos = new StaffAvailability(2L, LocalTime.of(9, 0), LocalTime.of(12, 0), List.of());

        List<SlotAssignment> result = calculator.calculateForStaff(date, 30, List.of(dolu, bos));

        // 10:00 slotu hala mevcut olmali (personel 2 musait) ve personel 2'ye atanmali.
        SlotAssignment slotAt10 = result.stream().filter(s -> s.time().equals(LocalTime.of(10, 0))).findFirst()
                .orElseThrow(() -> new AssertionError("10:00 slotu bulunamadi"));
        assertThat(slotAt10.assignedStaffId()).isEqualTo(2L);
    }

    @Test
    void enAzDoluPersoneleAtanir_farkEtmezKurali() {
        // Personel 1: gun boyu 3 randevusu var. Personel 2: hic randevusu yok.
        // Ikisi de test edilen slotta musait -- en az dolu olan (personel 2) secilmeli.
        List<BusyInterval> uc_randevu = List.of(
                new BusyInterval(LocalDateTime.of(date, LocalTime.of(9, 0)), LocalDateTime.of(date, LocalTime.of(9, 15))),
                new BusyInterval(LocalDateTime.of(date, LocalTime.of(13, 0)), LocalDateTime.of(date, LocalTime.of(13, 15))),
                new BusyInterval(LocalDateTime.of(date, LocalTime.of(15, 0)), LocalDateTime.of(date, LocalTime.of(15, 15))));
        StaffAvailability meşgulPersonel = new StaffAvailability(1L, LocalTime.of(9, 0), LocalTime.of(18, 0), uc_randevu);
        StaffAvailability bosPersonel = new StaffAvailability(2L, LocalTime.of(9, 0), LocalTime.of(18, 0), List.of());

        List<SlotAssignment> result = calculator.calculateForStaff(date, 15, List.of(meşgulPersonel, bosPersonel));

        // Ikisi de musait oldugu her slotta personel 2 secilmeli (0 < 3 mesguliyet).
        assertThat(result).filteredOn(s -> s.time().equals(LocalTime.of(10, 0)))
                .extracting(SlotAssignment::assignedStaffId)
                .containsExactly(2L);
    }

    @Test
    void hicPersonelYoksa_bosListeDoner() {
        List<SlotAssignment> result = calculator.calculateForStaff(date, 30, List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void gecersizHizmetSuresi_exceptionFirlatir() {
        StaffAvailability staff = new StaffAvailability(1L, LocalTime.of(9, 0), LocalTime.of(18, 0), List.of());

        assertThatThrownBy(() -> calculator.calculateForStaff(date, 0, List.of(staff)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void tumPersonelAyniSlottaMesgulse_slotHicDonmez() {
        BusyInterval busy = new BusyInterval(
                LocalDateTime.of(date, LocalTime.of(10, 0)),
                LocalDateTime.of(date, LocalTime.of(10, 30)));
        StaffAvailability p1 = new StaffAvailability(1L, LocalTime.of(9, 0), LocalTime.of(12, 0), List.of(busy));
        StaffAvailability p2 = new StaffAvailability(2L, LocalTime.of(9, 0), LocalTime.of(12, 0), List.of(busy));

        List<SlotAssignment> result = calculator.calculateForStaff(date, 30, List.of(p1, p2));

        assertThat(result).noneMatch(s -> s.time().equals(LocalTime.of(10, 0)));
    }
}
