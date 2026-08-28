package com.randevu.backend.service;

import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.Favorite;
import com.randevu.backend.entity.User;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.repository.BusinessRepository;
import com.randevu.backend.repository.FavoriteRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.time.Clock;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final BusinessRepository businessRepository;
    private final Clock clock;

    public FavoriteService(FavoriteRepository favoriteRepository, BusinessRepository businessRepository,
            Clock clock) {
        this.favoriteRepository = favoriteRepository;
        this.businessRepository = businessRepository;
        this.clock = clock;
    }

    // Kalp ikonu bir toggle -- zaten favorideyse ikinci POST hata vermez,
    // sessizce mevcut kaydı döner. "Zaten favorilerde" gibi bir hata
    // kullanıcıya anlamsız gelir, arayüzdeki tek işlev aç/kapa. User
    // parametre olarak tam entity -- BusinessService.createBusiness'teki
    // aynı desen (controller zaten CurrentUserService'ten tam nesneyi
    // almış durumda, tekrar ID'den sorgulamaya gerek yok).
    public Favorite addFavorite(User user, Long businessId) {
        return favoriteRepository.findByUser_IdAndBusiness_Id(user.getId(), businessId)
                .orElseGet(() -> {
                    Business business = businessRepository.findById(businessId)
                            .orElseThrow(() -> new ResourceNotFoundException("İşletme bulunamadı."));
                    Favorite favorite = Favorite.builder()
                            .user(user)
                            .business(business)
                            .createdAt(LocalDateTime.now(clock))
                            .build();
                    return favoriteRepository.save(favorite);
                });
    }

    // Aynı idempotent mantık: favoride değilse sessizce hiçbir şey yapmaz.
    public void removeFavorite(Long userId, Long businessId) {
        favoriteRepository.findByUser_IdAndBusiness_Id(userId, businessId)
                .ifPresent(favoriteRepository::delete);
    }

    public List<Business> getFavoriteBusinesses(Long userId) {
        return favoriteRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(Favorite::getBusiness)
                .toList();
    }

    public boolean isFavorited(Long userId, Long businessId) {
        return favoriteRepository.findByUser_IdAndBusiness_Id(userId, businessId).isPresent();
    }
}
