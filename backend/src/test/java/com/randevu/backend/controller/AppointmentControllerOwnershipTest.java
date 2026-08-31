package com.randevu.backend.controller;

import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.User;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.ServiceItemRepository;
import com.randevu.backend.repository.StaffRepository;
import com.randevu.backend.service.AppointmentService;
import com.randevu.backend.service.CurrentUserService;
import com.randevu.backend.service.OwnershipGuard;
import com.randevu.backend.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Faz 3.2 -- "A isletmesinin sahibi B'nin verisine erisemiyor" testleri.
// Eskiden bu manuel curl/Postman ile kontrol ediliyordu (bkz. CLAUDE.md /
// ROADMAP dogrulama listesi); artik otomatik. OwnershipGuard'i GERCEK
// nesneyle kuruyoruz (sadece BusinessRepository sahte) -- boylece hem
// guard'in kendi mantigi hem controller'in guard'i DOGRU parametreyle
// cagirdigi tek testte dogrulanmis oluyor. Sadece "guard cagrildi mi"
// diye mock'lasaydik, controller'a yanlis id (ornegin businessId yerine
// serviceId) gecirilse bile test yesil kalirdi -- asil regresyon riski budur.
@ExtendWith(MockitoExtension.class)
class AppointmentControllerOwnershipTest {

    private static final Long OWNER_ID = 1L;
    private static final Long ATTACKER_ID = 2L;
    private static final Long BUSINESS_ID = 100L;

    @Mock
    private AppointmentService appointmentService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ReviewService reviewService;
    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private ServiceItemRepository serviceItemRepository;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private Authentication authentication;

    private AppointmentController controller;

    @BeforeEach
    void setUp() {
        OwnershipGuard ownershipGuard = new OwnershipGuard(businessRepository, serviceItemRepository, staffRepository);
        controller = new AppointmentController(appointmentService, currentUserService, ownershipGuard, reviewService);

        Business business = Business.builder().id(BUSINESS_ID).owner(User.builder().id(OWNER_ID).build()).build();
        lenient().when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business));
    }

    private void actingAs(Long userId) {
        when(currentUserService.getCurrentUser(authentication)).thenReturn(User.builder().id(userId).build());
    }

    @Test
    @DisplayName("getBusinessAppointments: saldirgan baska isletmenin musteri listesini goremez")
    void getBusinessAppointments_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.getBusinessAppointments(BUSINESS_ID, authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(appointmentService, never()).getBusinessAppointments(any());
    }

    @Test
    @DisplayName("getBusinessAppointments: gercek sahip kendi randevu listesini gorebilir")
    void getBusinessAppointments_sahip_izinVerilir() {
        actingAs(OWNER_ID);
        when(appointmentService.getBusinessAppointments(BUSINESS_ID)).thenReturn(List.of());

        controller.getBusinessAppointments(BUSINESS_ID, authentication);

        verify(appointmentService).getBusinessAppointments(BUSINESS_ID);
    }

    @Test
    @DisplayName("getPendingAppointments (Istek Kutusu): saldirgan baska isletmenin bekleyen taleplerini goremez")
    void getPendingAppointments_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.getPendingAppointments(BUSINESS_ID, authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(appointmentService, never()).getPendingAppointmentsForBusiness(any());
    }

    @Test
    @DisplayName("getUpcomingBusinessAppointments: saldirgan baska isletmenin yaklasan randevularini goremez")
    void getUpcomingBusinessAppointments_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.getUpcomingBusinessAppointments(BUSINESS_ID, authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(appointmentService, never()).getUpcomingBusinessAppointments(any());
    }

    // Var olmayan bir isletmeye erisim denemesi -- OwnershipGuard'in kendi
    // 404 davranisinin controller uzerinden de dogru calistigini kanitlar.
    @Test
    @DisplayName("Var olmayan isletmeye erisim -- 403 degil 404 (ResourceNotFoundException)")
    void olmayanIsletmeye_erisim_ResourceNotFound() {
        Long olmayanId = 999L;
        when(businessRepository.findById(olmayanId)).thenReturn(Optional.empty());
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.getBusinessAppointments(olmayanId, authentication))
                .isInstanceOf(com.randevu.backend.exception.ResourceNotFoundException.class);
    }
}
