package com.randevu.backend.controller;

import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.User;
import com.randevu.backend.service.BusinessService;
import com.randevu.backend.service.CurrentUserService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses")
public class BusinessController {

    private final BusinessService businessService;
    private final CurrentUserService currentUserService;

    public BusinessController(BusinessService businessService, CurrentUserService currentUserService) {
        this.businessService = businessService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<Business> getAllBusinesses() {
        return businessService.getAllBusinesses();
    }

    // ownerId path'ten geliyor ama artık isteği atanın KENDİ id'siyle
    // eşleşmesi zorunlu — aksi halde herkes başka bir sahibin işletme
    // listesini görebilirdi (IDOR).
    @GetMapping("/owner/{ownerId}")
    public List<Business> getBusinessesByOwner(@PathVariable Long ownerId, Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        if (!currentUser.getId().equals(ownerId)) {
            throw new AccessDeniedException("Başka bir kullanıcının işletmelerini görüntüleyemezsiniz.");
        }
        return businessService.getBusinessesByOwner(ownerId);
    }

    // İşletme oluşturma — Token'dan sahip kimliği alınır, ownerId parametresi kaldırıldı
    @PostMapping("/create")
    public ResponseEntity<?> createBusiness(@RequestBody Business business,
                                            Authentication authentication) {
        User owner = currentUserService.getCurrentUser(authentication);
        return ResponseEntity.ok(businessService.createBusiness(owner, business));
    }

    // Belirtilen kategori adına göre işletmeleri getirir.
    @GetMapping("/category/{categoryName}")
    public ResponseEntity<List<Business>> getBusinessesByCategory(@PathVariable String categoryName) {
        List<Business> businesses = businessService.getBusinessesByCategory(categoryName);
        return ResponseEntity.ok(businesses);
    }

}
