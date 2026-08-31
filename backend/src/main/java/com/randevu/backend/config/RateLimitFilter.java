package com.randevu.backend.config;

import com.randevu.backend.ratelimit.RateLimitPort;
import com.randevu.backend.ratelimit.RateLimitResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

// Faz 3.5: IP bazli hacim siniri -- /register, /available-slots ve TUM
// /api/** icin genel guvenlik agi. Bunlar controller/servis katmaninda
// DEGIL burada, filtre seviyesinde: hicbirinde "kimlik dogrulanmis
// kullanici" gibi bir baglam yok (register'da henuz hesap yok,
// available-slots permitAll, genel guvenlik agi HER isteği kapsamali).
//
// GlobalExceptionHandler'i KULLANAMIYORUZ -- bu bir servlet filtresi,
// DispatcherServlet'e (dolayisiyla controller'lara/exception handler'lara)
// hic ulasmiyor (bkz. RestAuthenticationEntryPoint'teki ayni gerekce, ayni
// sebeple ObjectMapper da compile-time'da erisilebilir degil). Govde elle,
// ErrorResponse ile AYNI sekilde yaziliyor.
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitPort rateLimitPort;
    private final RateLimitProperties properties;
    private final Clock clock;

    public RateLimitFilter(RateLimitPort rateLimitPort, RateLimitProperties properties, Clock clock) {
        this.rateLimitPort = rateLimitPort;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String ip = request.getRemoteAddr();

        // Genel guvenlik agi HER /api/** istegine uygulanir -- kimlik
        // dogrulanmis olsun olmasin. Ozel bir kural (register/available-slots)
        // varsa o da AYRICA kontrol edilir; ikisi CAKISMAZ, UST USTE uygulanir
        // (katmanli savunma) -- ayni istek hem kendi ozel kuraliyla hem bu
        // tavanla sayilir.
        RateLimitResult globalResult = rateLimitPort.tryConsume(
                "global:ip:" + ip, properties.getGlobalMaxRequests(), properties.getGlobalWindow());

        Rule rule = ruleFor(request);
        RateLimitResult specificResult = rule == null
                ? new RateLimitResult(true, 0)
                : rateLimitPort.tryConsume("ip:" + ip + ":" + rule.name(), rule.maxRequests(), rule.window());

        if (!globalResult.allowed() || !specificResult.allowed()) {
            long retryAfter = Math.max(
                    globalResult.allowed() ? 0 : globalResult.retryAfterSeconds(),
                    specificResult.allowed() ? 0 : specificResult.retryAfterSeconds());
            writeTooManyRequests(response, request, retryAfter);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Rule ruleFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equals(method) && "/api/users/register".equals(path)) {
            return new Rule("register", properties.getRegisterMaxRequests(), properties.getRegisterWindow());
        }
        if ("GET".equals(method) && "/api/appointments/available-slots".equals(path)) {
            return new Rule("available-slots", properties.getAvailableSlotsMaxRequests(),
                    properties.getAvailableSlotsWindow());
        }
        return null;
    }

    private void writeTooManyRequests(HttpServletResponse response, HttpServletRequest request, long retryAfterSeconds)
            throws IOException {
        // Jakarta Servlet API'de SC_TOO_MANY_REQUESTS sabiti yok (429, orijinal
        // HTTP/1.1 durum kodlari kumesinde degil) -- Spring'in HttpStatus'undan
        // aliniyor, sihirli sayi (429) elle yazilmiyor.
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json;charset=UTF-8");

        String json = String.format(
                "{\"timestamp\":\"%s\",\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"%s\",\"path\":\"%s\"}",
                clock.instant(),
                "Çok fazla istek gönderdiniz. Lütfen bir süre sonra tekrar deneyin.",
                escapeJson(request.getRequestURI()));

        response.getWriter().write(json);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Rule(String name, int maxRequests, Duration window) {
    }
}
