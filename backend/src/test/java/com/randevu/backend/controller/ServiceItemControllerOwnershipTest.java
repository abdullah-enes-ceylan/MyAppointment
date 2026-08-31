package com.randevu.backend.controller;

import com.randevu.backend.dto.request.ServiceItemRequest;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.ServiceItem;
import com.randevu.backend.entity.User;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.ServiceItemRepository;
import com.randevu.backend.repository.StaffRepository;
import com.randevu.backend.service.CurrentUserService;
import com.randevu.backend.service.OwnershipGuard;
import com.randevu.backend.service.ServiceItemService;
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

// Faz 3.2. update/delete uclarinda businessId path'te yok, sadece serviceId
// var -- bu yuzden assertOwnsServiceItem'i test ediyoruz (guard once hizmeti
// bulup hangi isletmeye ait oldugunu kendi icinde cozuyor). Gecmiste bu
// kontrol yoktu: giris yapmis herhangi bir musteri rakip isletmenin
// fiyatini degistirebiliyor ya da hizmetini silebiliyordu (bkz.
// ServiceItemController'daki yorumlar) -- bu testler o acigin bir daha
// sessizce geri gelmeyecegini garanti ediyor.
@ExtendWith(MockitoExtension.class)
class ServiceItemControllerOwnershipTest {

    private static final Long OWNER_ID = 1L;
    private static final Long ATTACKER_ID = 2L;
    private static final Long BUSINESS_ID = 100L;
    private static final Long SERVICE_ID = 300L;

    @Mock
    private ServiceItemService serviceItemService;
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

    private ServiceItemController controller;
    private ServiceItem serviceItem;

    @BeforeEach
    void setUp() {
        OwnershipGuard ownershipGuard = new OwnershipGuard(businessRepository, serviceItemRepository, staffRepository);
        controller = new ServiceItemController(serviceItemService, currentUserService, ownershipGuard);

        Business business = Business.builder().id(BUSINESS_ID).owner(User.builder().id(OWNER_ID).build()).build();
        serviceItem = ServiceItem.builder().id(SERVICE_ID).business(business).name("Sac Kesimi").build();

        lenient().when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(business));
        lenient().when(serviceItemRepository.findById(SERVICE_ID)).thenReturn(Optional.of(serviceItem));
    }

    private void actingAs(Long userId) {
        when(currentUserService.getCurrentUser(authentication)).thenReturn(User.builder().id(userId).build());
    }

    @Test
    @DisplayName("createServiceItem: saldirgan rakip isletmeye hizmet ekleyemez")
    void createServiceItem_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.createServiceItem(BUSINESS_ID, new ServiceItemRequest(), authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(serviceItemService, never()).createServiceItem(any(), any());
    }

    @Test
    @DisplayName("createServiceItem: gercek sahip kendi isletmesine hizmet ekleyebilir")
    void createServiceItem_sahip_izinVerilir() {
        actingAs(OWNER_ID);
        when(serviceItemService.createServiceItem(eq(BUSINESS_ID), any())).thenReturn(serviceItem);

        controller.createServiceItem(BUSINESS_ID, new ServiceItemRequest(), authentication);

        verify(serviceItemService).createServiceItem(eq(BUSINESS_ID), any());
    }

    @Test
    @DisplayName("updateServiceItem: saldirgan rakip isletmenin fiyatini/suresini degistiremez")
    void updateServiceItem_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.updateServiceItem(SERVICE_ID, new ServiceItemRequest(), authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(serviceItemService, never()).updateService(any(), any());
    }

    @Test
    @DisplayName("updateServiceItem: gercek sahip kendi hizmetini guncelleyebilir")
    void updateServiceItem_sahip_izinVerilir() {
        actingAs(OWNER_ID);
        when(serviceItemService.updateService(eq(SERVICE_ID), any())).thenReturn(serviceItem);

        controller.updateServiceItem(SERVICE_ID, new ServiceItemRequest(), authentication);

        verify(serviceItemService).updateService(eq(SERVICE_ID), any());
    }

    @Test
    @DisplayName("deleteServiceItem: saldirgan rakip isletmenin hizmetini silemez")
    void deleteServiceItem_saldirgan_AccessDenied() {
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.deleteServiceItem(SERVICE_ID, authentication))
                .isInstanceOf(AccessDeniedException.class);

        verify(serviceItemService, never()).deleteService(any());
    }

    @Test
    @DisplayName("deleteServiceItem: gercek sahip kendi hizmetini silebilir")
    void deleteServiceItem_sahip_izinVerilir() {
        actingAs(OWNER_ID);

        controller.deleteServiceItem(SERVICE_ID, authentication);

        verify(serviceItemService).deleteService(SERVICE_ID);
    }

    @Test
    @DisplayName("Var olmayan hizmete erisim -- 404 (ResourceNotFoundException), 403 degil")
    void olmayanHizmete_erisim_ResourceNotFound() {
        Long olmayanId = 999L;
        when(serviceItemRepository.findById(olmayanId)).thenReturn(Optional.empty());
        actingAs(ATTACKER_ID);

        assertThatThrownBy(() -> controller.deleteServiceItem(olmayanId, authentication))
                .isInstanceOf(com.randevu.backend.exception.ResourceNotFoundException.class);
    }
}
