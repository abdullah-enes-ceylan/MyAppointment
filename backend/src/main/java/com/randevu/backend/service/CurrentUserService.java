package com.randevu.backend.service;

import com.randevu.backend.entity.User;
import com.randevu.backend.exception.ResourceNotFoundException;
import com.randevu.backend.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

// Spring Security'nin Authentication nesnesindeki email'den, o an istek atan
// gercek User kaydini bulur. Bu tek satırlık is (findByEmail + orElseThrow)
// AppointmentController'da iki kere, BusinessController'da bir kere kopya
// kopya yazilmisti (DRY ihlali) — hepsi buraya toplandi.
@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Token'dan gelen kimlikle veritabanindaki gercek kullaniciyi getirir.
    public User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı."));
    }
}
