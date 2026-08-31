package com.randevu.backend.controller;

import com.randevu.backend.dto.request.StaffRequest;
import com.randevu.backend.dto.request.StaffWorkingHourRequest;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.Staff;
import com.randevu.backend.entity.StaffWorkingHour;
import com.randevu.backend.entity.User;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.ServiceItemRepository;
import com.randevu.backend.repository.StaffRepository;
import com.randevu.backend.service.CurrentUserService;
import com.randevu.backend.service.OwnershipGuard;
import com.randevu.backend.service.StaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Faz 3.2. Iki farkli OwnershipGuard yolu ayni dosyada test ediliyor:
// businessId dogrudan path'teyken assertOwnsBusiness (getStaffByBusiness,
// createStaff), sadece staffId varken assertOwnsStaff (guncelleme/silme/
// mesai uclari -- guard once personeli bulup hangi isletmeye ait oldugunu
// kendi icinde cozuyor, bkz. OwnershipGuard.assertOwnsStaff).
@ExtendWith(MockitoExtension.class)
class StaffControllerOwnershipTest {

    private static final Long OWNER_ID = 1L;
    private static final Long ATTACKER_ID = 2L;
    private static final Long BUSINESS_ID = 100L;
    private static final Long STAFF_ID = 200L;

    @Mock
    private StaffService staffService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private ServiceItemRepository serviceItemRepository;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private Authentication authentication;

    private StaffController controller;
    private Staff staff;

    @BeforeEach
    void setUp() {
        OwnershipGuard ownershipGuard = new OwnershipGuard(businessRepository, serviceItemRepository, staffRepository);
        controller = new StaffController(staffService, currentUserService, ownershipGuard);

        Business business = Business.builder().id(BUSINESS_ID).owner(User.builder().id(OWNER_ID).build()).build();
        staff = Staff.builder().id(STAFF_ID).business(business).name("Test Personel").build();

        lenient().when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business));
        lenient().when(staffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff));
    }

    private void actingAs(Long userId) {
        when(currentUserService.getCurrentUser(authentication)).thenReturn(User.builder().id(userId).build());
    }

    // --- businessId uzerinden (assertOwnsBusiness) ---

    @Test
    @DisplayName("getStaffByBusiness: saldirgan rakip isletmenin calisan listesini goremez")
    void getStaffByBusiness_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.getStaffByBusiness(BUSINESS_ID, authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(staffService, never()).getStaffByBusiness(any());
    }

    @Test
    @DisplayName("createStaff: saldirgan rakip isletmeye personel ekleyemez")
    void createStaff_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.createStaff(BUSINESS_ID, new StaffRequest(), authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(staffService, never()).createStaff(any(), any());
    }

    @Test
    @DisplayName("createStaff: gercek sahip kendi isletmesine personel ekleyebilir")
    void createStaff_sahip_izinVerilir() {
        actingAs(OWNER_ID);
        when(staffService.createStaff(eq(BUSINESS_ID), any())).thenReturn(staff);

        controller.createStaff(BUSINESS_ID, new StaffRequest(), authentication);

        verify(staffService).createStaff(eq(BUSINESS_ID), any());
    }

    // --- staffId uzerinden (assertOwnsStaff) ---

    @Test
    @DisplayName("updateStaff: saldirgan rakip isletmenin personelini guncelleyemez")
    void updateStaff_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.updateStaff(STAFF_ID, new StaffRequest(), authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(staffService, never()).updateStaff(any(), any());
    }

    @Test
    @DisplayName("deleteStaff: saldirgan rakip isletmenin personelini silemez")
    void deleteStaff_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.deleteStaff(STAFF_ID, authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(staffService, never()).deleteStaff(any());
    }

    @Test
    @DisplayName("deleteStaff: gercek sahip kendi personelini silebilir")
    void deleteStaff_sahip_izinVerilir() {
        actingAs(OWNER_ID);

        controller.deleteStaff(STAFF_ID, authentication);

        verify(staffService).deleteStaff(STAFF_ID);
    }

    @Test
    @DisplayName("getWorkingHours (personel mesaisi): saldirgan rakip calisanin programini goremez")
    void getWorkingHours_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.getWorkingHours(STAFF_ID, authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(staffService, never()).getWorkingHours(any());
    }

    @Test
    @DisplayName("setWorkingHour: saldirgan rakip calisanin mesaisini degistiremez")
    void setWorkingHour_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.setWorkingHour(STAFF_ID, new StaffWorkingHourRequest(), authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(staffService, never()).setWorkingHour(any(), any());
    }

    @Test
    @DisplayName("setWorkingHour: gercek sahip kendi calisaninin mesaisini ayarlayabilir")
    void setWorkingHour_sahip_izinVerilir() {
        actingAs(OWNER_ID);
        when(staffService.setWorkingHour(eq(STAFF_ID), any())).thenReturn(StaffWorkingHour.builder().build());

        controller.setWorkingHour(STAFF_ID, new StaffWorkingHourRequest(), authentication);

        verify(staffService).setWorkingHour(eq(STAFF_ID), any());
    }

    @Test
    @DisplayName("Var olmayan personele erisim -- 404 (ResourceNotFoundException), 403 degil")
    void olmayanPersonele_erisim_ResourceNotFound() {
        Long olmayanId = 999L;
        when(staffRepository.findById(olmayanId)).thenReturn(Optional.empty());
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.deleteStaff(olmayanId, authentication))
                .isInstanceOf(com.randevu.backend.exception.ResourceNotFoundException.class);
    }
}
