package com.randevu.backend.controller;

import com.randevu.backend.dto.response.BusinessDetailResponse;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.BusinessPhoto;
import com.randevu.backend.entity.User;
import com.randevu.backend.mapper.BusinessMapper;
import com.randevu.backend.service.BusinessPhotoService;
import com.randevu.backend.service.BusinessService;
import com.randevu.backend.service.BusinessService.RatingStats;
import com.randevu.backend.service.CurrentUserService;
import com.randevu.backend.service.FavoriteService;
import com.randevu.backend.storage.BusinessPhotoStorage;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private final BusinessPhotoStorage photoStorage;
    private final BusinessPhotoService businessPhotoService;

    public FavoriteController(FavoriteService favoriteService, BusinessService businessService,
            CurrentUserService currentUserService, BusinessPhotoStorage photoStorage,
            BusinessPhotoService businessPhotoService) {
        this.favoriteService = favoriteService;
        this.businessService = businessService;
        this.currentUserService = currentUserService;
        this.photoStorage = photoStorage;
        this.businessPhotoService = businessPhotoService;
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
        List<Business> businesses = favoriteService.getFavoriteBusinesses(currentUser.getId());

        // Fotograflar (V17) BILEREK toplu cekiliyor -- bkz. BusinessController'daki
        // ayni gerekce (NOTLAR.md "N+1" notu). Bu uc da BusinessDetailResponse
        // dondugu icin (serviceItems gomulu) ayni riski tasiyordu.
        Map<Long, List<BusinessPhoto>> photosByBusiness = businessPhotoService.getPhotosGroupedByBusinessId(
                businesses.stream().map(Business::getId).toList());

        return businesses.stream()
                .map(business -> {
                    RatingStats stats = businessService.getRatingStats(business.getId());
                    List<BusinessPhoto> photos = photosByBusiness.getOrDefault(business.getId(), Collections.emptyList());
                    return BusinessMapper.toDetailResponse(business, stats.averageRating(), stats.reviewCount(),
                            photos, photoStorage);
                })
                .toList();
    }
}
