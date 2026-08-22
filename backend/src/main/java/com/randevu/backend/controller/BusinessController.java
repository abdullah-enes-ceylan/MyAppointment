package com.randevu.backend.controller;

import com.randevu.backend.dto.request.BusinessRequest;
import com.randevu.backend.dto.response.BusinessDetailResponse;
import com.randevu.backend.dto.response.BusinessResponse;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.User;
import com.randevu.backend.mapper.BusinessMapper;
import com.randevu.backend.service.BusinessService;
import com.randevu.backend.service.BusinessService.RatingStats;
import com.randevu.backend.service.CurrentUserService;
import com.randevu.backend.service.OwnershipGuard;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses")
public class BusinessController {

    private final BusinessService businessService;
    private final CurrentUserService currentUserService;
    private final OwnershipGuard ownershipGuard;

    public BusinessController(BusinessService businessService, CurrentUserService currentUserService,
                               OwnershipGuard ownershipGuard) {
        this.businessService = businessService;
        this.currentUserService = currentUserService;
        this.ownershipGuard = ownershipGuard;
    }

    // BusinessDetailResponse dönüyor (hizmetler gömülü) — frontend şu an
    // tüm listeyi buradan çekip client'ta filtreliyor ve serviceItems'a
    // ihtiyaç duyuyor. Eskiden entity dönüyordu; herkese açık (permitAll)
    // bu uçta her işletme sahibinin email/telefon/rolü çıplak sızıyordu.
    @GetMapping
    public List<BusinessDetailResponse> getAllBusinesses() {
        return businessService.getAllBusinesses().stream()
                .map(this::toDetailResponseWithRating)
                .toList();
    }

    // YENİ: tekil işletme detayı. Eskiden bu uç hiç yoktu — frontend
    // BusinessDetailPage.jsx tüm listeyi çekip client'ta id'ye göre
    // filtreliyordu, 14 işletmede sorun değil ama 500 işletmede felaket
    // olurdu. {id:\d+} ile SADECE sayısal path'lere eşleşiyor — bu sayede
    // hem SecurityConfig'teki permitAll kuralı hem MVC yönlendirmesi
    // /my gibi başka literal path'lerle asla çakışmıyor.
    // Herkese açık: müsaitlik saatlerinde olduğu gibi, randevu almadan
    // önce müşterinin işletme detayını görebilmesi gerekiyor.
    @GetMapping("/{id:\\d+}")
    public BusinessDetailResponse getBusinessById(@PathVariable Long id) {
        return toDetailResponseWithRating(businessService.getBusinessById(id));
    }

    // YENİ: kendi işletmelerim. Eskiden /owner/{ownerId} idi — path'teki
    // ID'nin isteği atanın KENDİ id'siyle eşleşmesi zaten zorunluydu (Faz
    // 0.4), yani parametre fiilen gereksizdi. /appointments/me ile aynı
    // desene taşındı: kimlik daima token'dan gelir, path'te taşınmaz.
    // Frontend bu eski uca hiç bağlı değildi (grep ile doğrulandı).
    @GetMapping("/my")
    public List<BusinessResponse> getMyBusinesses(Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        return businessService.getBusinessesByOwner(currentUser.getId()).stream()
                .map(this::toResponseWithRating)
                .toList();
    }

    // İşletme oluşturma — Token'dan sahip kimliği alınır. Artık Business
    // entity'si değil BusinessRequest DTO alıyor — istemci "owner" veya
    // "id" gönderemez (mass assignment kapalı, bkz. o DTO'daki açıklama).
    @PostMapping("/create")
    public ResponseEntity<BusinessResponse> createBusiness(@Valid @RequestBody BusinessRequest request,
                                            Authentication authentication) {
        User owner = currentUserService.getCurrentUser(authentication);
        Business created = businessService.createBusiness(owner, BusinessMapper.toEntity(request));
        return ResponseEntity.ok(toResponseWithRating(created));
    }

    // YENİ: işletme güncelleme, sahiplik kontrollü. OwnershipGuard olmadan,
    // giriş yapmış herhangi bir kullanıcı başka bir işletmenin bilgilerini
    // (adres, saat, fiyat aralığı vb.) değiştirebilirdi.
    @PutMapping("/{id:\\d+}")
    public BusinessResponse updateBusiness(@PathVariable("id") Long businessId,
                                            @Valid @RequestBody BusinessRequest request,
                                            Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        ownershipGuard.assertOwnsBusiness(currentUser.getId(), businessId);
        Business updated = businessService.updateBusiness(businessId, request);
        return toResponseWithRating(updated);
    }

    // Belirtilen kategori adına göre işletmeleri getirir.
    @GetMapping("/category/{categoryName}")
    public ResponseEntity<List<BusinessDetailResponse>> getBusinessesByCategory(@PathVariable String categoryName) {
        List<BusinessDetailResponse> businesses = businessService.getBusinessesByCategory(categoryName).stream()
                .map(this::toDetailResponseWithRating)
                .toList();
        return ResponseEntity.ok(businesses);
    }

    // Faz 2.7: her işletme yanıtına puan ortalaması + yorum sayısı ekliyor.
    // İş listesi başına bir sorgu (N+1) — bilerek: 5-10 işletmelik beta
    // ölçeğinde önemsiz, erken optimizasyon yapmıyoruz (bkz. ROADMAP 2.7).
    private BusinessResponse toResponseWithRating(Business business) {
        RatingStats stats = businessService.getRatingStats(business.getId());
        return BusinessMapper.toResponse(business, stats.averageRating(), stats.reviewCount());
    }

    private BusinessDetailResponse toDetailResponseWithRating(Business business) {
        RatingStats stats = businessService.getRatingStats(business.getId());
        return BusinessMapper.toDetailResponse(business, stats.averageRating(), stats.reviewCount());
    }

}
