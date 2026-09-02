package com.randevu.backend.service;

import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.ServiceItem;
import com.randevu.backend.entity.Staff;
import com.randevu.backend.exception.BusinessRuleException;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.ServiceItemRepository;
import com.randevu.backend.repository.StaffRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

// Multi-tenant izolasyonunun tek geçtiği yer: bir işletmeye ait veriye erişmeden
// önce, o işletmenin gerçekten isteği atan kullanıcıya ait olduğunu doğrular.
// Bu kontrol olmadan, giriş yapmış herhangi bir kullanıcı businessId'yi
// değiştirerek başka bir işletmenin randevularını/müşteri bilgilerini
// okuyabiliyordu (bkz. ROADMAP K2). Kontrolü tek bir yerde tutmak — her
// controller'a aynı if'i kopyalamak yerine — hem DRY hem de ileride bu
// kuralın nasıl çalıştığını değiştirmek istediğimizde (örn. Faz 2'de
// personel bazlı yetkilendirme) tek nokta değişikliği sağlıyor.
@Service
public class OwnershipGuard {

    private final BusinessRepository businessRepository;
    private final ServiceItemRepository serviceItemRepository;
    private final StaffRepository staffRepository;

    public OwnershipGuard(BusinessRepository businessRepository, ServiceItemRepository serviceItemRepository,
            StaffRepository staffRepository) {
        this.businessRepository = businessRepository;
        this.serviceItemRepository = serviceItemRepository;
        this.staffRepository = staffRepository;
    }

    // businessId'nin gerçekten userId'ye ait olduğunu doğrular.
    // İşletme yoksa 404, başkasınınsa 403 fırlatır. Askida olsa bile
    // GEÇER -- bu SADECE bir sahiplik/okuma kontrolü, "assertOwnsActiveBusiness"
    // ile KARIŞTIRILMAMALI (bkz. onun açıklaması). Sahip kendi askıdaki
    // işletmesini panelinden GÖRMEYE devam etmeli (Faz 3.9).
    public void assertOwnsBusiness(Long userId, Long businessId) {
        getOwnedBusiness(userId, businessId);
    }

    // Faz 3.9: MUTASYON uçları için -- sahiplik yetmez, işletme ayrıca askıda
    // olmamalı. Hesap silme talep edilmiş bir işletme salt-okunur moda
    // geçer: sahip kendi verisini (randevular, inbox, personel listesi)
    // görmeye devam eder ama profilini/hizmetini/personelini/çalışma
    // saatlerini DEĞİŞTİREMEZ -- tek aktif aksiyon
    // POST /api/users/me/cancel-deletion. Aksi halde silme sürecindeki bir
    // işletme "kapanıyorum" derken yeni hizmet/personel ekleyip müşteriyi
    // yanıltabilirdi.
    public void assertOwnsActiveBusiness(Long userId, Long businessId) {
        Business business = getOwnedBusiness(userId, businessId);
        requireActive(business);
    }

    private Business getOwnedBusiness(Long userId, Long businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("İşletme bulunamadı."));

        if (!business.getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("Bu işletmenin verilerine erişim yetkiniz yok.");
        }
        return business;
    }

    private void requireActive(Business business) {
        if (business.getSuspendedAt() != null) {
            throw new BusinessRuleException(
                    "İşletmeniz hesap silme sürecinde olduğu için bu işlem yapılamıyor. "
                            + "Devam etmek için hesap silme talebinizi iptal edin.");
        }
    }

    // ServiceItem update/delete uçlarında businessId doğrudan URL'de yok —
    // sadece serviceId var. Önce hizmeti bulup hangi işletmeye ait olduğunu
    // öğreniyor, sonra o işletmenin sahipliğini assertOwnsBusiness ile
    // (aynı kod tekrar yazılmadan) doğruluyor.
    public void assertOwnsServiceItem(Long userId, Long serviceItemId) {
        assertOwnsBusiness(userId, findServiceItemBusinessId(userId, serviceItemId));
    }

    // assertOwnsServiceItem'in mutasyon-uçları versiyonu (bkz.
    // assertOwnsActiveBusiness'teki gerekçe).
    public void assertOwnsActiveServiceItem(Long userId, Long serviceItemId) {
        assertOwnsActiveBusiness(userId, findServiceItemBusinessId(userId, serviceItemId));
    }

    private Long findServiceItemBusinessId(Long userId, Long serviceItemId) {
        ServiceItem serviceItem = serviceItemRepository.findById(serviceItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Hizmet bulunamadı."));
        return serviceItem.getBusiness().getId();
    }

    // Personel update/delete/working-hours uçlarında businessId doğrudan
    // URL'de yok — assertOwnsServiceItem ile aynı desen.
    public void assertOwnsStaff(Long userId, Long staffId) {
        assertOwnsBusiness(userId, findStaffBusinessId(staffId));
    }

    // assertOwnsStaff'ın mutasyon-uçları versiyonu (bkz.
    // assertOwnsActiveBusiness'teki gerekçe).
    public void assertOwnsActiveStaff(Long userId, Long staffId) {
        assertOwnsActiveBusiness(userId, findStaffBusinessId(staffId));
    }

    private Long findStaffBusinessId(Long staffId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Personel bulunamadı."));
        return staff.getBusiness().getId();
    }
}
