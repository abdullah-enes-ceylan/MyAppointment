package com.randevu.backend.controller;

import com.randevu.backend.dto.request.ChangePasswordRequest;
import com.randevu.backend.dto.request.DeleteAccountRequest;
import com.randevu.backend.dto.request.RegisterRequest;
import com.randevu.backend.dto.request.UpdateProfileRequest;
import com.randevu.backend.dto.response.DeletionImpactResponse;
import com.randevu.backend.dto.response.ProfileStatsResponse;
import com.randevu.backend.dto.response.UserResponse;
import com.randevu.backend.entity.User;
import com.randevu.backend.mapper.UserMapper;
import com.randevu.backend.service.AccountDeletionService;
import com.randevu.backend.service.AccountDeletionService.DeletionDeadlines;
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
    private final AccountDeletionService accountDeletionService;

    public UserController(UserService userService, CurrentUserService currentUserService,
            ProfileStatsService profileStatsService, AccountDeletionService accountDeletionService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
        this.profileStatsService = profileStatsService;
        this.accountDeletionService = accountDeletionService;
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

    // Kendi profil bilgilerim. deletionRequestedAt DOLUYSA (Faz 3.9),
    // frontend'in banner'da gosterecegi hazir deadline'lari da hesaplayip
    // ekliyoruz -- bkz. AccountDeletionService.computeDeadlines'teki gerekce.
    @GetMapping("/me")
    public UserResponse getMyProfile(Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        DeletionDeadlines deadlines = accountDeletionService.computeDeadlines(currentUser);
        return UserMapper.toResponse(currentUser, deadlines.identityAnonymizationDeadlineAt(),
                deadlines.businessReversalDeadlineAt());
    }

    // Ad/soyad/telefon guncelleme. E-posta ve rol degistirilemez
    // (bkz. UpdateProfileRequest'teki aciklama).
    @PutMapping("/me")
    public UserResponse updateMyProfile(@Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        User updated = userService.updateProfile(currentUser, request);
        DeletionDeadlines deadlines = accountDeletionService.computeDeadlines(updated);
        return UserMapper.toResponse(updated, deadlines.identityAnonymizationDeadlineAt(),
                deadlines.businessReversalDeadlineAt());
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

    // Hesap silme talebi (Faz 3.9, KVKK unutulma hakki). Sifre yeniden
    // istenir (bkz. DeleteAccountRequest). Bu cagridan sonra USER icin
    // deletionRequestedAt disinda HICBIR SEY degismez -- giris, randevular,
    // favoriler 30 gun boyunca aynen kalir. BUSINESS_OWNER icin ek olarak
    // isletmeler aninda askiya alinir, yakin randevular hemen iptal edilir
    // (bkz. AccountDeletionService).
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(@Valid @RequestBody DeleteAccountRequest request,
            Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        accountDeletionService.requestDeletion(currentUser, request.getPassword());
        return ResponseEntity.noContent().build();
    }

    // Silme talebi ONAYLANMADAN ONCE onay diyaloğunun gösterdiği etki
    // önizlemesi -- BUSINESS_OWNER için "N randevunuz iptal edilecek" uyarısı
    // buradan besleniyor (USER'da her zaman 0, bkz. DeletionImpactResponse).
    // Salt okunur, hiçbir şeyi değiştirmiyor -- şifre de istemiyor, sadece
    // bir sayı gösteriyor.
    @GetMapping("/me/deletion-impact")
    public DeletionImpactResponse getMyDeletionImpact(Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        return new DeletionImpactResponse(accountDeletionService.previewAffectedAppointmentCount(currentUser));
    }

    // Silme talebini geri alir. gracePeriod dolmadigi surece (anonymizedAt
    // hala null) her zaman mumkun -- bu, geri donus penceresinin butun
    // amaci (bkz. User.java'daki gerekce).
    @PostMapping("/me/cancel-deletion")
    public ResponseEntity<Void> cancelMyAccountDeletion(Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        accountDeletionService.cancelDeletion(currentUser);
        return ResponseEntity.noContent().build();
    }
}
