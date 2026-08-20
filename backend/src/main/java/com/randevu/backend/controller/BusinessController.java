package com.randevu.backend.controller;

import com.randevu.backend.dto.response.BusinessDetailResponse;
import com.randevu.backend.dto.response.BusinessResponse;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.User;
import com.randevu.backend.mapper.BusinessMapper;
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

    // BusinessDetailResponse dönüyor (hizmetler gömülü) — dedicated
    // GET /businesses/{id} henüz yok (Faz 1.5), frontend şu an tüm listeyi
    // buradan çekip client'ta filtreliyor ve serviceItems'a ihtiyaç duyuyor.
    // Eskiden entity dönüyordu; herkese açık (permitAll) bu uçta her
    // işletme sahibinin email/telefon/rolü çıplak sızıyordu.
    @GetMapping
    public List<BusinessDetailResponse> getAllBusinesses() {
        return businessService.getAllBusinesses().stream()
                .map(BusinessMapper::toDetailResponse)
                .toList();
    }

    // ownerId path'ten geliyor ama artık isteği atanın KENDİ id'siyle
    // eşleşmesi zorunlu — aksi halde herkes başka bir sahibin işletme
    // listesini görebilirdi (IDOR).
    @GetMapping("/owner/{ownerId}")
    public List<BusinessResponse> getBusinessesByOwner(@PathVariable Long ownerId, Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        if (!currentUser.getId().equals(ownerId)) {
            throw new AccessDeniedException("Başka bir kullanıcının işletmelerini görüntüleyemezsiniz.");
        }
        return businessService.getBusinessesByOwner(ownerId).stream()
                .map(BusinessMapper::toResponse)
                .toList();
    }

    // İşletme oluşturma — Token'dan sahip kimliği alınır, ownerId parametresi kaldırıldı
    @PostMapping("/create")
    public ResponseEntity<BusinessResponse> createBusiness(@RequestBody Business business,
                                            Authentication authentication) {
        User owner = currentUserService.getCurrentUser(authentication);
        Business created = businessService.createBusiness(owner, business);
        return ResponseEntity.ok(BusinessMapper.toResponse(created));
    }

    // Belirtilen kategori adına göre işletmeleri getirir.
    @GetMapping("/category/{categoryName}")
    public ResponseEntity<List<BusinessDetailResponse>> getBusinessesByCategory(@PathVariable String categoryName) {
        List<BusinessDetailResponse> businesses = businessService.getBusinessesByCategory(categoryName).stream()
                .map(BusinessMapper::toDetailResponse)
                .toList();
        return ResponseEntity.ok(businesses);
    }

}
