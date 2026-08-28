package com.randevu.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;

// Token yoksa/geçersizse/süresi dolmuşsa devreye giren nokta. Bu, DispatcherServlet'e
// (yani controller'lara) hiç ulaşmadan, Spring Security'nin filtre zincirinde
// gerçekleşir — o yüzden GlobalExceptionHandler bunu YAKALAYAMAZ, o sadece
// controller'dan çıkan exception'ları görür. Bu bean olmadan Spring, httpBasic
// ve formLogin devre dışı bırakıldığı için kendi varsayılanı olan
// Http403ForbiddenEntryPoint'i kullanıyordu — token'sız her istek 401 yerine
// 403 dönüyordu (frontend'in axios interceptor'ı sadece 401'de oturumu
// sonlandırıyordu, bu yüzden hiç çalışmıyordu).
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    // Hata zaman damgasi da uygulamanin TEK saat kaynagindan geliyor.
    // Sebep sadece tutarlilik degil: bir sorunu arastirirken hata
    // yanitindaki saat ile is verisindeki saat (randevu, yorum, favori)
    // farkli kaynaklardan gelirse zaman cizelgesi cikarilamaz.
    private final Clock clock;

    public RestAuthenticationEntryPoint(Clock clock) {
        this.clock = clock;
    }

    // Not: GlobalExceptionHandler'ın ErrorResponse'unu burada kullanmıyoruz.
    // Bu proje "spring-boot-starter-web" değil "spring-boot-starter-webmvc"
    // kullanıyor ve Jackson'ın ObjectMapper'ı compile-time'da erişilebilir
    // değil (sadece runtime'da, Spring'in kendi mesaj dönüştürücüsü
    // üzerinden dolaylı olarak var). Govde sabit şekilli ve içeriği bizim
    // kontrolümüzde olduğu için (path hariç, o da bir URI, tırnak/ters slash
    // içermez) elle JSON üretmek burada güvenli ve ekstra bağımlılık gerektirmiyor.
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        String json = String.format(
                "{\"timestamp\":\"%s\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\",\"path\":\"%s\"}",
                clock.instant(),
                "Bu işlem için giriş yapmanız gerekiyor.",
                escapeJson(request.getRequestURI()));

        response.getWriter().write(json);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
