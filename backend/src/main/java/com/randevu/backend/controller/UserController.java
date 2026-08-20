package com.randevu.backend.controller;

import com.randevu.backend.dto.request.RegisterRequest;
import com.randevu.backend.entity.User;
import com.randevu.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    // Yeni kullanici kaydi olusturan API. Email zaten kayitliysa UserService'in
    // firlattigi EmailAlreadyExistsException artik burada yakalanmiyor —
    // GlobalExceptionHandler onu 409'a ceviriyor (controller artik hata
    // govdesi uretme isiyle ugrasmiyor, SRP).
    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@Valid @RequestBody RegisterRequest request) {
        User createdUser = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }
}
