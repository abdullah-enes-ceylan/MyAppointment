package com.randevu.backend.service;

import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.ServiceItem;
import com.randevu.backend.entity.Staff;
import com.randevu.backend.entity.User;
import com.randevu.backend.exception.BusinessRuleException;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.ServiceItemRepository;
import com.randevu.backend.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// Faz 3.9: "assertOwnsBusiness" (okuma -- askıda olsa bile geçer) ile
// "assertOwnsActiveBusiness" (mutasyon -- askıdaysa 409) arasındaki ayrımın
// GERÇEKTEN korunduğunu kanıtlar. Bu ayrım karışırsa iki yönde de hata
// olur: ya sahip kendi askıdaki işletmesini panelinden hiç göremez (okuma
// yanlışlıkla engellenir), ya da askıdaki bir işletme yeni hizmet/personel
// ekleyebilir (mutasyon yanlışlıkla izin verilir).
@ExtendWith(MockitoExtension.class)
class OwnershipGuardTest {

    private static final Long OWNER_ID = 1L;
    private static final Long STRANGER_ID = 2L;
    private static final Long BUSINESS_ID = 100L;
    private static final Long SERVICE_ITEM_ID = 200L;
    private static final Long STAFF_ID = 300L;

    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private ServiceItemRepository serviceItemRepository;
    @Mock
    private StaffRepository staffRepository;

    private OwnershipGuard ownershipGuard;
    private Business activeBusiness;
    private Business suspendedBusiness;

    @BeforeEach
    void setUp() {
        ownershipGuard = new OwnershipGuard(businessRepository, serviceItemRepository, staffRepository);

        User owner = User.builder().id(OWNER_ID).build();
        activeBusiness = Business.builder().id(BUSINESS_ID).owner(owner).build();
        suspendedBusiness = Business.builder().id(BUSINESS_ID).owner(owner)
                .suspendedAt(LocalDateTime.of(2026, 9, 1, 10, 0)).build();
    }

    @Test
    @DisplayName("assertOwnsBusiness: askıdaki işletme için de GEÇER -- bu sadece okuma/sahiplik kontrolü")
    void assertOwnsBusiness_askidakiIsletmedeDeGecer() {
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(suspendedBusiness));

        assertThatCode(() -> ownershipGuard.assertOwnsBusiness(OWNER_ID, BUSINESS_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertOwnsActiveBusiness: aktif işletmede geçer")
    void assertOwnsActiveBusiness_aktifIsletmedeGecer() {
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(activeBusiness));

        assertThatCode(() -> ownershipGuard.assertOwnsActiveBusiness(OWNER_ID, BUSINESS_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertOwnsActiveBusiness: askıdaki işletmede 409 (BusinessRuleException) fırlatır")
    void assertOwnsActiveBusiness_askidakiIsletmedeReddeder() {
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(suspendedBusiness));

        assertThatThrownBy(() -> ownershipGuard.assertOwnsActiveBusiness(OWNER_ID, BUSINESS_ID))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("hesap silme sürecinde");
    }

    @Test
    @DisplayName("assertOwnsActiveBusiness: başkasının işletmesinde askıda olsa da olmasa da 403")
    void assertOwnsActiveBusiness_baskasininIsletmesindeYetkisiz() {
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(activeBusiness));

        assertThatThrownBy(() -> ownershipGuard.assertOwnsActiveBusiness(STRANGER_ID, BUSINESS_ID))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("assertOwnsActiveServiceItem: askıdaki işletmenin hizmetinde 409")
    void assertOwnsActiveServiceItem_askidakiIsletmedeReddeder() {
        ServiceItem serviceItem = ServiceItem.builder().id(SERVICE_ITEM_ID).business(suspendedBusiness).build();
        when(serviceItemRepository.findById(SERVICE_ITEM_ID)).thenReturn(Optional.of(serviceItem));
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(suspendedBusiness));

        assertThatThrownBy(() -> ownershipGuard.assertOwnsActiveServiceItem(OWNER_ID, SERVICE_ITEM_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("assertOwnsServiceItem (okuma): askıdaki işletmenin hizmetinde de geçer")
    void assertOwnsServiceItem_askidakiIsletmedeDeGecer() {
        ServiceItem serviceItem = ServiceItem.builder().id(SERVICE_ITEM_ID).business(suspendedBusiness).build();
        when(serviceItemRepository.findById(SERVICE_ITEM_ID)).thenReturn(Optional.of(serviceItem));
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(suspendedBusiness));

        assertThatCode(() -> ownershipGuard.assertOwnsServiceItem(OWNER_ID, SERVICE_ITEM_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertOwnsActiveStaff: askıdaki işletmenin personelinde 409")
    void assertOwnsActiveStaff_askidakiIsletmedeReddeder() {
        Staff staff = Staff.builder().id(STAFF_ID).business(suspendedBusiness).build();
        when(staffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff));
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(suspendedBusiness));

        assertThatThrownBy(() -> ownershipGuard.assertOwnsActiveStaff(OWNER_ID, STAFF_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("assertOwnsStaff (okuma): askıdaki işletmenin personelinde de geçer")
    void assertOwnsStaff_askidakiIsletmedeDeGecer() {
        Staff staff = Staff.builder().id(STAFF_ID).business(suspendedBusiness).build();
        when(staffRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff));
        when(businessRepository.findById(BUSINESS_ID)).thenReturn(Optional.of(suspendedBusiness));

        assertThatCode(() -> ownershipGuard.assertOwnsStaff(OWNER_ID, STAFF_ID)).doesNotThrowAnyException();
    }
}
