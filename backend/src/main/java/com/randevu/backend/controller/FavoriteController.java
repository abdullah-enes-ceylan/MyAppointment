package com.randevu.backend.controller;

import com.randevu.backend.dto.response.BusinessDetailResponse;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.User;
import com.randevu.backend.mapper.BusinessMapper;
import com.randevu.backend.service.BusinessService;
import com.randevu.backend.service.BusinessService.RatingStats;
import com.randevu.backend.service.CurrentUserService;
import com.randevu.backend.service.FavoriteService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// businessId path'te ama "kimin favorisi" sorusu HİÇ path'te taşınmıyor --
// her uç, token'dan çözülen kullanıcının KENDİ favorileri üzerinde çalışır.
// Bu yüzden Staff/Business'taki gibi bir OwnershipGuard kontrolüne burada
// gerek yok: "başkasının favorisine eriş" senaryosu path parametreleriyle
// hiç ifade edilemiyor. Tüm uçlar SecurityConfig'teki genel
// ".requestMatchers("/api/**").authenticated()" kuralına tabi.
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final BusinessService businessService;
    private final CurrentUserService currentUserService;

    public FavoriteController(FavoriteService favoriteService, BusinessService businessService,
            CurrentUserService currentUserService) {
        this.favoriteService = favoriteService;
        this.businessService = businessService;
        this.currentUserService = currentUserService;
    }

    // Kalp ikonu toggle'ının "aç" ucu -- idempotent, zaten favorideyse
    // hata vermez (bkz. FavoriteService).
    @PostMapping("/{businessId}")
    public ResponseEntity<Void> addFavorite(@PathVariable Long businessId, Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        favoriteService.addFavorite(currentUser, businessId);
        return ResponseEntity.ok().build();
    }

    // Toggle'ın "kapa" ucu -- aynı şekilde idempotent.
    @DeleteMapping("/{businessId}")
    public ResponseEntity<Void> removeFavorite(@PathVariable Long businessId, Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        favoriteService.removeFavorite(currentUser.getId(), businessId);
        return ResponseEntity.noContent().build();
    }

    // DetailResponse (BusinessResponse değil) BİLEREK -- serviceItems gömülü
    // gelsin diye: frontend "bugün en erken müsaitlik" rozetini hesaplarken
    // ilk hizmeti buradan okuyor (bkz. HomePage/FavoritesPage'deki
    // earliestSlots effect'i, GET /api/businesses ile aynı sözleşme).
    @GetMapping("/me")
    public List<BusinessDetailResponse> getMyFavorites(Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        return favoriteService.getFavoriteBusinesses(currentUser.getId()).stream()
                .map(this::toDetailResponseWithRating)
                .toList();
    }

    private BusinessDetailResponse toDetailResponseWithRating(Business business) {
        RatingStats stats = businessService.getRatingStats(business.getId());
        return BusinessMapper.toDetailResponse(business, stats.averageRating(), stats.reviewCount());
    }
}
