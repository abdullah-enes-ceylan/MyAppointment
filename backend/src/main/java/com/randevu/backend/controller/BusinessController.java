package com.randevu.backend.controller;

import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.User;
import com.randevu.backend.repository.UserRepository;
import com.randevu.backend.service.BusinessService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses")
public class BusinessController {

    private final BusinessService businessService;
    private final UserRepository userRepository;

    public BusinessController(BusinessService businessService, UserRepository userRepository) {
        this.businessService = businessService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<Business> getAllBusinesses() {
        return businessService.getAllBusinesses();
    }

    @GetMapping("/owner/{ownerId}")
    public List<Business> getBusinessesByOwner(@PathVariable Long ownerId) {
        return businessService.getBusinessesByOwner(ownerId);
    }

    // İşletme oluşturma — Token'dan sahip kimliği alınır, ownerId parametresi kaldırıldı
    @PostMapping("/create")
    public ResponseEntity<?> createBusiness(@RequestBody Business business,
                                            Authentication authentication) {
        String email = authentication.getName();
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı."));

        return ResponseEntity.ok(businessService.createBusiness(owner, business));
    }

    // Belirtilen kategori adına göre işletmeleri getirir.
    @GetMapping("/category/{categoryName}")
    public ResponseEntity<List<Business>> getBusinessesByCategory(@PathVariable String categoryName) {
        List<Business> businesses = businessService.getBusinessesByCategory(categoryName);
        return ResponseEntity.ok(businesses);
    }

}