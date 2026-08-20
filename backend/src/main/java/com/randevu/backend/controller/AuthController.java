package com.randevu.backend.controller;

import com.randevu.backend.config.JwtUtil;
import com.randevu.backend.dto.LoginRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

// @CrossOrigin("*") buradan kaldırıldı — SecurityConfig'deki global CORS
// bean'i (corsConfigurationSource) zaten localhost origin'lerine izin
// veriyor. Bu satır ayrıca "*" + allowCredentials(true) çelişkisi
// taşıyordu; tarayıcılar bu kombinasyonu CORS spesifikasyonu gereği
// geçersiz sayar ve isteği reddedebilir.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    // Modern yöntem: Constructor Injection (Eski moda @Autowired yerine)
    public AuthController(AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService,
            JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            // 1. Şifre Doğru mu?
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));
        } catch (BadCredentialsException e) {
            // Yanlışsa uygulamanın çökmesini engeller, temiz bir 401 hatası döner
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Hatalı email veya şifre!");
        }

        // 2. Doğruysa Kullanıcıyı Bul
        final UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getEmail());

        // 3. Bileti (JWT) Bas
        final String jwt = jwtUtil.generateToken(userDetails);

        // 4. Bileti Frontend'e Yolla (Ekstra AuthResponse dosyası açmamıza gerek
        // kalmadan)
        Map<String, String> response = new HashMap<>();
        response.put("token", jwt);

        return ResponseEntity.ok(response);
    }
}