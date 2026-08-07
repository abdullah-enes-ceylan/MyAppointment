package com.randevu.backend.service;

import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.Role;
import com.randevu.backend.entity.User;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

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
                .orElseThrow(() -> new RuntimeException("Dükkan sahibi bulunamadı"));
        return businessRepository.findByOwnerId(owner.getId());
    }

    public Business createBusiness(Long ownerId, Business business) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("Dükkan sahibi bulunamadı"));

        // User -> BusinessOwner yapıyoruz 
        if (owner.getRole() == Role.USER) {
            owner.setRole(Role.BUSINESS_OWNER);
            userRepository.save(owner);
        }

        business.setOwner(owner);
        return businessRepository.save(business);
    }

}
