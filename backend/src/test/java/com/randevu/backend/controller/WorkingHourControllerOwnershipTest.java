package com.randevu.backend.controller;

import com.randevu.backend.dto.request.BusinessClosureRequest;
import com.randevu.backend.dto.request.WorkingHourRequest;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.BusinessClosure;
import com.randevu.backend.entity.User;
import com.randevu.backend.entity.WorkingHour;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.ServiceItemRepository;
import com.randevu.backend.repository.StaffRepository;
import com.randevu.backend.service.CurrentUserService;
import com.randevu.backend.service.OwnershipGuard;
import com.randevu.backend.service.WorkingHourService;
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

// Faz 3.2. Calisma saatleri/kapanislarin GET uclari bilerek herkese acik
// (musteri randevu almadan once "bu isletme Pazar acik mi" gorebilmeli),
// sadece degistiren uclar (PUT/POST/DELETE) sahiplik kontrollu -- bu dosya
// sadece o degistiren uclari test ediyor.
@ExtendWith(MockitoExtension.class)
class WorkingHourControllerOwnershipTest {

    private static final Long OWNER_ID = 1L;
    private static final Long ATTACKER_ID = 2L;
    private static final Long BUSINESS_ID = 100L;
    private static final Long CLOSURE_ID = 400L;

    @Mock
    private WorkingHourService workingHourService;
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

    private WorkingHourController controller;

    @BeforeEach
    void setUp() {
        OwnershipGuard ownershipGuard = new OwnershipGuard(businessRepository, serviceItemRepository, staffRepository);
        controller = new WorkingHourController(workingHourService, currentUserService, ownershipGuard);

        Business business = Business.builder().id(BUSINESS_ID).owner(User.builder().id(OWNER_ID).build()).build();
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business));
    }

    private void actingAs(Long userId) {
        when(currentUserService.getCurrentUser(authentication)).thenReturn(User.builder().id(userId).build());
    }

    @Test
    @DisplayName("setWorkingHour: saldirgan rakip isletmenin calisma saatini degistiremez")
    void setWorkingHour_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.setWorkingHour(BUSINESS_ID, new WorkingHourRequest(), authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(workingHourService, never()).setWorkingHour(any(), any());
    }

    @Test
    @DisplayName("setWorkingHour: gercek sahip kendi calisma saatini ayarlayabilir")
    void setWorkingHour_sahip_izinVerilir() {
        actingAs(OWNER_ID);
        when(workingHourService.setWorkingHour(eq(BUSINESS_ID), any())).thenReturn(WorkingHour.builder().build());

        controller.setWorkingHour(BUSINESS_ID, new WorkingHourRequest(), authentication);

        verify(workingHourService).setWorkingHour(eq(BUSINESS_ID), any());
    }

    @Test
    @DisplayName("addClosure: saldirgan rakip isletmeye kapanis gunu ekleyemez")
    void addClosure_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.addClosure(BUSINESS_ID, new BusinessClosureRequest(), authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(workingHourService, never()).addClosure(any(), any());
    }

    @Test
    @DisplayName("addClosure: gercek sahip kendi isletmesine kapanis gunu ekleyebilir")
    void addClosure_sahip_izinVerilir() {
        actingAs(OWNER_ID);
        when(workingHourService.addClosure(eq(BUSINESS_ID), any())).thenReturn(BusinessClosure.builder().build());

        controller.addClosure(BUSINESS_ID, new BusinessClosureRequest(), authentication);

        verify(workingHourService).addClosure(eq(BUSINESS_ID), any());
    }

    @Test
    @DisplayName("removeClosure: saldirgan rakip isletmenin kapanis gununu silemez")
    void removeClosure_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.removeClosure(BUSINESS_ID, CLOSURE_ID, authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(workingHourService, never()).removeClosure(any(), any());
    }

    @Test
    @DisplayName("removeClosure: gercek sahip kendi isletmesinin kapanis gununu silebilir")
    void removeClosure_sahip_izinVerilir() {
        actingAs(OWNER_ID);

        controller.removeClosure(BUSINESS_ID, CLOSURE_ID, authentication);

        verify(workingHourService).removeClosure(BUSINESS_ID, CLOSURE_ID);
    }
}
