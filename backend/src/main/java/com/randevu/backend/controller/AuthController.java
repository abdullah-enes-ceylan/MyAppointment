package com.randevu.backend.controller;

import com.randevu.backend.config.JwtUtil;
import com.randevu.backend.dto.LoginRequest;
import com.randevu.backend.dto.response.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

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

    // try/catch KALDIRILDI: BadCredentialsException artik
    // GlobalExceptionHandler'da yakalanip 401 + standart ErrorResponse'a
    // ceviriliyor (bkz. oradaki aciklama). Eskiden burada yakalanip govdeye
    // duz string yaziliyordu ve API'nin tek "farkli sekilli" yaniti buydu.
    // Controller artik sadece HTTP cevirisi yapiyor, hata govdesi uretmiyor.
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest loginRequest) {
        // Sifre dogru mu? Yanlissa BadCredentialsException firlar ve buradan
        // disari cikar -- yakalamiyoruz, handler'in isi.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getEmail());
        return new LoginResponse(jwtUtil.generateToken(userDetails));
    }
}