package com.randevu.backend.controller;

import com.randevu.backend.dto.request.BusinessRequest;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.User;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.ServiceItemRepository;
import com.randevu.backend.repository.StaffRepository;
import com.randevu.backend.service.BusinessPhotoService;
import com.randevu.backend.service.BusinessService;
import com.randevu.backend.service.BusinessService.RatingStats;
import com.randevu.backend.service.CurrentUserService;
import com.randevu.backend.service.LocationService;
import com.randevu.backend.service.OwnershipGuard;
import com.randevu.backend.storage.BusinessPhotoStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Faz 3.2 -- ayni desen: OwnershipGuard GERCEK, sadece BusinessRepository
// sahte. Buradaki uc uc, giris yapmis herhangi bir kullanicinin baska
// isletmenin temel bilgilerini/kapak fotografini degistirebildigi (gecmiste
// gercekten yasanmis) acigi kapatiyor -- bkz. BusinessController'daki
// "OwnershipGuard olmadan..." yorumlari.
@ExtendWith(MockitoExtension.class)
class BusinessControllerOwnershipTest {

    private static final Long OWNER_ID = 1L;
    private static final Long ATTACKER_ID = 2L;
    private static final Long BUSINESS_ID = 100L;

    @Mock
    private BusinessService businessService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private LocationService locationService;
    @Mock
    private BusinessPhotoStorage photoStorage;
    @Mock
    private BusinessPhotoService businessPhotoService;
    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private ServiceItemRepository serviceItemRepository;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private Authentication authentication;
    @Mock
    private MultipartFile file;

    private BusinessController controller;
    private Business business;

    @BeforeEach
    void setUp() {
        OwnershipGuard ownershipGuard = new OwnershipGuard(businessRepository, serviceItemRepository, staffRepository);
        controller = new BusinessController(businessService, currentUserService, ownershipGuard, locationService,
                photoStorage, businessPhotoService);

        business = Business.builder().id(BUSINESS_ID).owner(User.builder().id(OWNER_ID).build()).build();
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business));
    }

    private void actingAs(Long userId) {
        when(currentUserService.getCurrentUser(authentication)).thenReturn(User.builder().id(userId).build());
    }

    @Test
    @DisplayName("updateBusiness: saldirgan baska isletmenin bilgilerini degistiremez")
    void updateBusiness_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.updateBusiness(BUSINESS_ID, new BusinessRequest(), authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(businessService, never()).updateBusiness(any(), any());
    }

    @Test
    @DisplayName("updateBusiness: gercek sahip kendi isletmesini guncelleyebilir")
    void updateBusiness_sahip_izinVerilir() {
        actingAs(OWNER_ID);
        when(businessService.updateBusiness(eq(BUSINESS_ID), any())).thenReturn(business);
        when(businessService.getRatingStats(BUSINESS_ID)).thenReturn(new RatingStats(null, 0));

        controller.updateBusiness(BUSINESS_ID, new BusinessRequest(), authentication);

        verify(businessService).updateBusiness(eq(BUSINESS_ID), any());
    }

    @Test
    @DisplayName("uploadPhoto: saldirgan baska isletmenin kapak fotografini degistiremez")
    void uploadPhoto_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.uploadPhoto(BUSINESS_ID, file, authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(businessPhotoService, never()).uploadPhoto(any(), any());
    }

    @Test
    @DisplayName("uploadPhoto: gercek sahip kendi kapak fotografini yukleyebilir")
    void uploadPhoto_sahip_izinVerilir() {
        actingAs(OWNER_ID);
        when(businessPhotoService.uploadPhoto(BUSINESS_ID, file)).thenReturn(business);
        when(businessService.getRatingStats(BUSINESS_ID)).thenReturn(new RatingStats(null, 0));

        controller.uploadPhoto(BUSINESS_ID, file, authentication);

        verify(businessPhotoService).uploadPhoto(BUSINESS_ID, file);
    }

    @Test
    @DisplayName("removePhoto: saldirgan baska isletmenin kapak fotografini silemez")
    void removePhoto_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.removePhoto(BUSINESS_ID, authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(businessPhotoService, never()).removePhoto(any());
    }

    @Test
    @DisplayName("removePhoto: gercek sahip kendi kapak fotografini silebilir")
    void removePhoto_sahip_izinVerilir() {
        actingAs(OWNER_ID);

        controller.removePhoto(BUSINESS_ID, authentication);

        verify(businessPhotoService).removePhoto(BUSINESS_ID);
    }
}
