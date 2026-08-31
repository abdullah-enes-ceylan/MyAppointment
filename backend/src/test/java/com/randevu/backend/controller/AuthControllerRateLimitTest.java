package com.randevu.backend.controller;

import com.randevu.backend.config.JwtUtil;
import com.randevu.backend.config.RateLimitProperties;
import com.randevu.backend.dto.LoginRequest;
import com.randevu.backend.exception.RateLimitExceededException;
import com.randevu.backend.ratelimit.InMemoryRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

// Faz 3.5 -- login kaba-kuvvet korumasindaki HESAP ve IP limitlerinin
// GERCEKTEN birbirinden BAGIMSIZ calistigini kanitlar. InMemoryRateLimiter
// GERCEK (mock degil) -- amac sadece "AuthController dogru metodu cagiriyor
// mu" degil, iki sayacin GERCEKTEN ayri anahtar uzaylarinda yasadigini,
// birinin dolmasinin digerini etkilemedigini kanitlamak.
//
// doThrow/doReturn KASITLI kullanildi, when(mock.method()).thenX() DEGIL:
// bir mock zaten firlatacak sekilde stub'lanmisken when(...) ile YENIDEN
// stub'lamaya calismak, when()'in kendisi metodu KAYIT icin gercekten
// cagirdigi icin (bu da onceki stub'i tetikler) testin kendisini patlatir.
// doThrow/doReturn metodu hic cagirmadan stub kurar, bu sorunu yasamaz.
@ExtendWith(MockitoExtension.class)
class AuthControllerRateLimitTest {

    private static final int ACCOUNT_MAX_ATTEMPTS = 3;
    private static final int IP_MAX_ATTEMPTS = 5;

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private JwtUtil jwtUtil;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneOffset.UTC);
        InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter(clock);

        RateLimitProperties properties = new RateLimitProperties();
        properties.setLoginAccountMaxAttempts(ACCOUNT_MAX_ATTEMPTS);
        properties.setLoginAccountWindow(Duration.ofMinutes(15));
        properties.setLoginIpMaxAttempts(IP_MAX_ATTEMPTS);
        properties.setLoginIpWindow(Duration.ofMinutes(15));

        controller = new AuthController(authenticationManager, userDetailsService, jwtUtil, rateLimiter, properties);

        // Varsayilan: her sifre yanlis. Testler basarili giris denemesi icin
        // bunu doReturn ile ezip degistirir (bkz. asagidaki gerekce).
        doThrow(new BadCredentialsException("bad")).when(authenticationManager).authenticate(any());
    }

    private MockHttpServletRequest requestFrom(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    private LoginRequest loginRequest(String email) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword("yanlis-sifre");
        return request;
    }

    private void attemptLoginExpectingBadCredentials(String email, String ip) {
        assertThatThrownBy(() -> controller.login(loginRequest(email), requestFrom(ip)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Hesap limiti dolunca, AYNI IP'den BASKA bir hesap etkilenmez")
    void hesapLimitiDolunca_ayniIpdenBaskaHesapEtkilenmez() {
        String ip = "1.1.1.1";

        // victim@example.com hesabini tam limite kadar (3) basarisiz dene --
        // hesap kilitlenir.
        for (int i = 0; i < ACCOUNT_MAX_ATTEMPTS; i++) {
            attemptLoginExpectingBadCredentials("victim@example.com", ip);
        }
        assertThatThrownBy(() -> controller.login(loginRequest("victim@example.com"), requestFrom(ip)))
                .isInstanceOf(RateLimitExceededException.class);

        // AYNI IP'den BASKA bir hesap (other@example.com) -- IP sayaci henuz
        // (3/5) IP limitinin altinda, bu yuzden hesap bazinda hic denenmemis
        // olan bu hesap BLOKE OLMAMALI, normal 401 (BadCredentialsException)
        // almalı, RateLimitExceededException DEGIL.
        attemptLoginExpectingBadCredentials("other@example.com", ip);
    }

    @Test
    @DisplayName("IP limiti dolunca, o IP'den DENENMEMIS taze bir hesap bile bloklanir")
    void ipLimitiDolunca_taze_hesapBileBloklanir() {
        String ip = "2.2.2.2";

        // 5 FARKLI hesabi, her birini SADECE BIR KEZ, ayni IP'den basarisiz
        // dene -- hicbiri kendi hesap limitine (3) ulasmiyor ama IP toplami
        // 5'e (IP limiti) ulasiyor.
        for (int i = 1; i <= IP_MAX_ATTEMPTS; i++) {
            attemptLoginExpectingBadCredentials("user" + i + "@example.com", ip);
        }

        // Bu IP'den DAHA ONCE HIC denenmemis, taze bir 6. hesap -- kendi
        // hesap sayaci sifir olmasina ragmen IP limiti yuzunden BLOKE
        // edilmeli. Bu, "coklu hesap deneyerek IP bazli kacis" senaryosunun
        // GERCEKTEN yakalandiginin kaniti.
        assertThatThrownBy(() -> controller.login(loginRequest("brand-new@example.com"), requestFrom(ip)))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("Basarili giris HESAP sayacini temizler ama IP sayacini ETKILEMEZ")
    void basariliGiris_hesapSayaciniTemizlerIpSayaciniEtkilemez() {
        String ip = "3.3.3.3";
        String email = "regular@example.com";

        // Hesabi limitin bir eksigine kadar (2/3) basarisiz dene.
        attemptLoginExpectingBadCredentials(email, ip);
        attemptLoginExpectingBadCredentials(email, ip);

        // Simdi basarili giris -- authenticate() artik istisna FIRLATMIYOR.
        // doReturn KULLANILDI (when(...) degil): authenticationManager zaten
        // "throw" olarak stub'li, when(mock.authenticate(...)) yazmak
        // mock'u gercekten cagirip o istisnayi hemen firlatirdi.
        doReturn(null).when(authenticationManager).authenticate(any());
        doReturn(User.withUsername(email).password("x").authorities("USER").build())
                .when(userDetailsService).loadUserByUsername(email);
        doReturn("dummy-token").when(jwtUtil).generateToken(any());

        assertThatCode(() -> controller.login(loginRequest(email), requestFrom(ip))).doesNotThrowAnyException();

        // Basarili giristen SONRA authenticate() tekrar basarisiz olacak
        // sekilde ayarlaniyor -- ayni doThrow/doReturn gerekcesi.
        doThrow(new BadCredentialsException("bad")).when(authenticationManager).authenticate(any());

        // HESAP sayaci sifirlandi mi: ayni hesaba tekrar 3 basarisiz deneme
        // (sifirlanmamis olsaydi "eskiden 2 + yeni 1 = 3" ile HEMEN
        // bloklanirdi) hala normal 401 vermeli, sadece TAM 3. denemeden
        // SONRAKI (4.) bloklanmali.
        attemptLoginExpectingBadCredentials(email, ip);
        attemptLoginExpectingBadCredentials(email, ip);
        attemptLoginExpectingBadCredentials(email, ip);
        assertThatThrownBy(() -> controller.login(loginRequest(email), requestFrom(ip)))
                .isInstanceOf(RateLimitExceededException.class);
    }
}
