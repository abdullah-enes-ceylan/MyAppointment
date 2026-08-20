package com.randevu.backend.service;

import com.randevu.backend.entity.Business;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.repository.BusinessRepository;
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

    public OwnershipGuard(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    // businessId'nin gerçekten userId'ye ait olduğunu doğrular.
    // İşletme yoksa 404, başkasınınsa 403 fırlatır.
    public void assertOwnsBusiness(Long userId, Long businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("İşletme bulunamadı."));

        if (!business.getOwner().getId().equals(userId)) {
            throw new AccessDeniedException("Bu işletmenin verilerine erişim yetkiniz yok.");
        }
    }
}
