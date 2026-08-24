package com.randevu.backend.controller;

import com.randevu.backend.dto.request.ChangePasswordRequest;
import com.randevu.backend.dto.request.RegisterRequest;
import com.randevu.backend.dto.request.UpdateProfileRequest;
import com.randevu.backend.dto.response.ProfileStatsResponse;
import com.randevu.backend.dto.response.UserResponse;
import com.randevu.backend.entity.User;
import com.randevu.backend.mapper.UserMapper;
import com.randevu.backend.service.CurrentUserService;
import com.randevu.backend.service.ProfileStatsService;
import com.randevu.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final CurrentUserService currentUserService;
    private final ProfileStatsService profileStatsService;

    public UserController(UserService userService, CurrentUserService currentUserService,
            ProfileStatsService profileStatsService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
        this.profileStatsService = profileStatsService;
    }

    // Tum kullanicilari getiren API — tum kullanicilarin ad/email/telefon
    // bilgisini dondugu icin sadece ADMIN erisebilir. @PreAuthorize burada
    // ilk kez kullaniliyor: SecurityConfig'deki @EnableMethodSecurity
    // olmadan bu anotasyon SESSIZCE yok sayilir, hicbir hata vermez —
    // yani calistigini gormek icin mutlaka once o anotasyonun eklendiginden
    // emin olmak lazim. İfade JWT'deki role claim'ini DEGIL, JwtFilter'in
    // her istekte veritabanindan taze yukledigi UserDetails.getAuthorities()
    // degerini kontrol eder (bkz. CustomUserDetailsService).
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.getAllUsers().stream()
                .map(UserMapper::toResponse)
                .toList();
    }

    // Yeni kullanici kaydi olusturan API. Email zaten kayitliysa UserService'in
    // firlattigi EmailAlreadyExistsException artik burada yakalanmiyor —
    // GlobalExceptionHandler onu 409'a ceviriyor (controller artik hata
    // govdesi uretme isiyle ugrasmiyor, SRP).
    @PostMapping("/register")
    public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody RegisterRequest request) {
        User createdUser = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserMapper.toResponse(createdUser));
    }

    // Asagidaki uc "/me" ucunun ortak ozelligi: kimlik HIC path'te
    // tasinmiyor, daima token'dan cozuluyor. /appointments/me ve
    // /businesses/my ile ayni desen -- kullanicinin kendi ID'sini URL'de
    // tasimasi IDOR'un tanimidir (bkz. ROADMAP Faz 0.4).

    // Kendi profil bilgilerim.
    @GetMapping("/me")
    public UserResponse getMyProfile(Authentication authentication) {
        return UserMapper.toResponse(currentUserService.getCurrentUser(authentication));
    }

    // Ad/soyad/telefon guncelleme. E-posta ve rol degistirilemez
    // (bkz. UpdateProfileRequest'teki aciklama).
    @PutMapping("/me")
    public UserResponse updateMyProfile(@Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        return UserMapper.toResponse(userService.updateProfile(currentUser, request));
    }

    // Sifre degistirme -- profil guncellemeden ayri, cunku mevcut sifrenin
    // dogrulanmasi gerekiyor (bkz. ChangePasswordRequest).
    @PutMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        userService.changePassword(currentUser, request);
        return ResponseEntity.noContent().build();
    }

    // Profil ekranindaki ozet sayilar.
    @GetMapping("/me/stats")
    public ProfileStatsResponse getMyStats(Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        return profileStatsService.getStatsForUser(currentUser.getId());
    }
}
