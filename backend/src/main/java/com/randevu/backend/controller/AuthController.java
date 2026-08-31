package com.randevu.backend.controller;

import com.randevu.backend.config.JwtUtil;
import com.randevu.backend.config.RateLimitProperties;
import com.randevu.backend.dto.LoginRequest;
import com.randevu.backend.dto.response.LoginResponse;
import com.randevu.backend.exception.RateLimitExceededException;
import com.randevu.backend.ratelimit.RateLimitPort;
import com.randevu.backend.ratelimit.RateLimitResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
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
    private final RateLimitPort rateLimitPort;
    private final RateLimitProperties rateLimitProperties;

    // Iki ayri anahtar uzayi -- RateLimitFilter'in IP anahtarlariyla da
    // (bkz. o sinif) hic karismasin diye ayri on ekler.
    private static final String LOGIN_ACCOUNT_KEY_PREFIX = "login:account:";
    private static final String LOGIN_IP_KEY_PREFIX = "login:ip:";

    // Modern yöntem: Constructor Injection (Eski moda @Autowired yerine)
    public AuthController(AuthenticationManager authenticationManager,
            UserDetailsService userDetailsService,
            JwtUtil jwtUtil,
            RateLimitPort rateLimitPort,
            RateLimitProperties rateLimitProperties) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
        this.rateLimitPort = rateLimitPort;
        this.rateLimitProperties = rateLimitProperties;
    }

    // Faz 3.5: kaba-kuvvet korumasi -- HESAP ve IP limitleri BAGIMSIZ,
    // ikisi de kontrol edilir. Sadece hesap limiti olsaydi saldirgan farkli
    // hesaplari deneyerek kacardi (hicbiri tek basina esige ulasmaz);
    // sadece IP limiti olsaydi dagitik (cok IP'den tek hesaba) saldiri
    // gecerdi. Biri digerinin kacis yolunu kapatiyor.
    //
    // ASIMETRIK reset KASITLI: basarili giriste SADECE hesap sayaci
    // temizlenir (bu hesabin sahibi sifresini dogru bildigini kanitladi).
    // IP sayaci ASLA basariyla temizlenmez -- bu IP'den GECERLI bir hesaba
    // girilmis olmasi, o IP'nin ayni anda BASKA hesaplari denemedigini
    // GARANTI ETMEZ (ele gecirilmis/paylasilan bir makine, ya da saldirgan
    // "gercek" bir hesapla arada bir basarili giris yapip IP'sinin
    // sayacini sifirlamaya calisiyor olabilir).
    //
    // try/catch sadece basarisizligi SAYMAK icin var -- BadCredentialsException
    // hala disari firliyor (yakalanip yutulmuyor), GlobalExceptionHandler
    // onu eskisi gibi 401'e ceviriyor.
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String accountKey = LOGIN_ACCOUNT_KEY_PREFIX + loginRequest.getEmail().toLowerCase();
        String ipKey = LOGIN_IP_KEY_PREFIX + request.getRemoteAddr();

        RateLimitResult accountCheck = rateLimitPort.checkBlocked(
                accountKey, rateLimitProperties.getLoginAccountMaxAttempts(), rateLimitProperties.getLoginAccountWindow());
        RateLimitResult ipCheck = rateLimitPort.checkBlocked(
                ipKey, rateLimitProperties.getLoginIpMaxAttempts(), rateLimitProperties.getLoginIpWindow());

        if (!accountCheck.allowed() || !ipCheck.allowed()) {
            long retryAfter = Math.max(
                    accountCheck.allowed() ? 0 : accountCheck.retryAfterSeconds(),
                    ipCheck.allowed() ? 0 : ipCheck.retryAfterSeconds());
            throw new RateLimitExceededException(
                    "Çok fazla başarısız giriş denemesi yapıldı. Lütfen bir süre sonra tekrar deneyin.", retryAfter);
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));
        } catch (BadCredentialsException e) {
            rateLimitPort.recordFailure(accountKey, rateLimitProperties.getLoginAccountWindow());
            rateLimitPort.recordFailure(ipKey, rateLimitProperties.getLoginIpWindow());
            throw e;
        }
        rateLimitPort.reset(accountKey);

        UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getEmail());
        return new LoginResponse(jwtUtil.generateToken(userDetails));
    }
}
