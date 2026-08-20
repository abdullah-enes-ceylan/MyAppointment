package com.randevu.backend.service;

import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.BusinessCategory;
import com.randevu.backend.entity.Role;
import com.randevu.backend.entity.User;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class BusinessService {
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;

    public BusinessService(BusinessRepository businessRepository, UserRepository userRepository) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
    }

    public List<Business> getAllBusinesses() {
        return businessRepository.findAll();
    }

    public List<Business> getBusinessesByOwner(Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Dükkan sahibi bulunamadı"));
        return businessRepository.findByOwnerId(owner.getId());
    }

    // Bu metot iki ayri kayit yapiyor: once kullanicinin rolunu yukseltiyor,
    // sonra isletmeyi kaydediyor. @Transactional olmadan, ikinci save() (ornegin
    // isletme adi/adresi bos oldugu icin) patlarsa, birinci save() ZATEN
    // COMMIT EDILMIS olur — kullanici isletmesi olmadan BUSINESS_OWNER kalir.
    // @Transactional, ikisini TEK bir islem (transaction) olarak sarar: biri
    // basarisiz olursa Spring OTOMATIK ROLLBACK yapar, ikisi de geri alinir.
    @Transactional
    public Business createBusiness(User owner, Business business) {
        // User -> BusinessOwner yapıyoruz
        if (owner.getRole() == Role.USER) {
            owner.setRole(Role.BUSINESS_OWNER);
            userRepository.save(owner);
        }

        business.setOwner(owner);
        return businessRepository.save(business);
    }

    // Gelen metni Enum'a çevirir ve filtreler. Geçersiz kategorilerde boş liste
    // döner.
    public List<Business> getBusinessesByCategory(String categoryStr) {
        try {
            BusinessCategory category = BusinessCategory.valueOf(categoryStr.toUpperCase());
            return businessRepository.findByCategory(category);
        } catch (IllegalArgumentException e) {
            return new ArrayList<>();
        }
    }

}
