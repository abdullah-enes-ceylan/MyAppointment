package com.randevu.backend.controller;

import com.randevu.backend.config.RateLimitProperties;
import com.randevu.backend.dto.request.BusinessRequest;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.BusinessPhoto;
import com.randevu.backend.entity.User;
import com.randevu.backend.ratelimit.RateLimitPort;
import com.randevu.backend.ratelimit.RateLimitResult;
import com.randevu.backend.repository.BusinessPhotoRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    // IDOR testi icin: saldirganin HICBIR iliskisi olmayan, BASKA bir
    // isletmeye ait bir fotograf (bkz. removePhoto_fotografBaskaIsletmeye_AccessDenied).
    private static final Long OTHER_BUSINESS_ID = 200L;
    private static final Long PHOTO_ID = 55L;

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
    private BusinessPhotoRepository businessPhotoRepository;
    @Mock
    private Authentication authentication;
    @Mock
    private MultipartFile file;
    @Mock
    private RateLimitPort rateLimitPort;
    @Mock
    private RateLimitProperties rateLimitProperties;

    private BusinessController controller;
    private Business business;

    @BeforeEach
    void setUp() {
        OwnershipGuard ownershipGuard = new OwnershipGuard(businessRepository, serviceItemRepository, staffRepository,
                businessPhotoRepository);
        controller = new BusinessController(businessService, currentUserService, ownershipGuard, locationService,
                photoStorage, businessPhotoService, rateLimitPort, rateLimitProperties);

        business = Business.builder().id(BUSINESS_ID).owner(User.builder().id(OWNER_ID).build()).build();
        // lenient: removePhoto_fotografBaskaIsletmeye_AccessDenied testi
        // BUSINESS_ID'yi HIC sorgulamiyor (fotografin GERCEK isletmesi
        // OTHER_BUSINESS_ID uzerinden cozuluyor) -- bu ortak stub o testte
        // kullanilmadigi icin strict-stubs UnnecessaryStubbingException
        // firlatirdi (bkz. NOTLAR.md "Mockito UnnecessaryStubbingException"
        // notu). Digerlerinin cogu hala buna ihtiyac duyuyor.
        lenient().when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business));
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
    @DisplayName("addPhoto: saldirgan baska isletmeye fotograf ekleyemez")
    void addPhoto_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.addPhoto(BUSINESS_ID, file, authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(businessPhotoService, never()).addPhoto(any(), any());
    }

    @Test
    @DisplayName("addPhoto: gercek sahip kendi isletmesine fotograf ekleyebilir")
    void addPhoto_sahip_izinVerilir() {
        actingAs(OWNER_ID);
        when(rateLimitPort.tryConsume(any(), anyInt(), any())).thenReturn(new RateLimitResult(true, 0));
        when(businessPhotoService.addPhoto(BUSINESS_ID, file)).thenReturn(List.of());

        controller.addPhoto(BUSINESS_ID, file, authentication);

        verify(businessPhotoService).addPhoto(BUSINESS_ID, file);
    }

    @Test
    @DisplayName("removePhoto: saldirgan baska isletmenin fotografini silemez")
    void removePhoto_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);
        BusinessPhoto photo = BusinessPhoto.builder().id(PHOTO_ID).business(business).build();
        when(businessPhotoRepository.findById(PHOTO_ID)).thenReturn(Optional.of(photo));

        assertThatThrownBy(() -> controller.removePhoto(BUSINESS_ID, PHOTO_ID, authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(businessPhotoService, never()).removePhoto(any(), any());
    }

    // IDOR testi (bkz. NOTLAR.md) -- path'teki businessId'ye GUVENMIYORUZ.
    // Fotograf GERCEKTE baska bir isletmeye (OTHER_BUSINESS_ID, saldirganin
    // sahibi OLMADIGI) ait; saldirgan kendi BUSINESS_ID'sini path'e yazsa
    // bile OwnershipGuard fotografin GERCEK isletmesini bulup onu
    // dogruluyor -- path'teki id'yle degil.
    @Test
    @DisplayName("removePhoto: fotograf path'teki isletmeye degil baska bir isletmeye aitse AccessDenied (IDOR)")
    void removePhoto_fotografBaskaIsletmeye_AccessDenied() {
        actingAs(ATTACKER_ID);
        Business otherBusiness = Business.builder().id(OTHER_BUSINESS_ID)
                .owner(User.builder().id(999L).build()).build();
        BusinessPhoto photo = BusinessPhoto.builder().id(PHOTO_ID).business(otherBusiness).build();
        when(businessPhotoRepository.findById(PHOTO_ID)).thenReturn(Optional.of(photo));
        when(businessRepository.findById(OTHER_BUSINESS_ID)).thenReturn(Optional.of(otherBusiness));

        assertThatThrownBy(() -> controller.removePhoto(BUSINESS_ID, PHOTO_ID, authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(businessPhotoService, never()).removePhoto(any(), any());
    }

    @Test
    @DisplayName("removePhoto: gercek sahip kendi fotografini silebilir")
    void removePhoto_sahip_izinVerilir() {
        actingAs(OWNER_ID);
        BusinessPhoto photo = BusinessPhoto.builder().id(PHOTO_ID).business(business).build();
        when(businessPhotoRepository.findById(PHOTO_ID)).thenReturn(Optional.of(photo));
        when(businessPhotoService.removePhoto(BUSINESS_ID, PHOTO_ID)).thenReturn(List.of());

        controller.removePhoto(BUSINESS_ID, PHOTO_ID, authentication);

        verify(businessPhotoService).removePhoto(BUSINESS_ID, PHOTO_ID);
    }
}
