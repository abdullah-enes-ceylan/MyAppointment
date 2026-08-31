package com.randevu.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
// @PreAuthorize gibi metot-seviyesi anotasyonları etkinleştirir. Bu olmadan
// controller/service metotlarının üstüne @PreAuthorize yazsan bile Spring
// onu hiç okumaz — sessizce yok sayılır, hiçbir hata da vermez. Bu yüzden
// bu satırı unutmak, yetkilendirmenin "orada duruyor ama çalışmıyor"
// şeklinde sessizce kırılmasına yol açan sinsi bir hatadır.
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    public SecurityConfig(JwtFilter jwtFilter, RestAuthenticationEntryPoint restAuthenticationEntryPoint) {
        this.jwtFilter = jwtFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                // JWT ile çalışan bir API'nin sunucu tarafında oturum (session)
                // tutmasına gerek yok — her istek kendi kimliğini token'ında
                // taşıyor. STATELESS, Spring'in JSESSIONID cookie'si üretmesini
                // ve session tabanlı SecurityContext saklamayı tamamen kapatır.
                // Alternatifi (varsayılan IF_REQUIRED) gereksiz sunucu belleği
                // tüketir ve yatay ölçeklenmeyi (birden fazla sunucu kopyası)
                // zorlaştırır — her sunucunun kendi session'ı ayrı olur.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/users/register").permitAll()
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/businesses").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/businesses/nearby").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/businesses/category/**").permitAll()
                        // {id:\d+} kısıtı: SADECE sayısal path'lere eşleşir, "/my" gibi
                        // kimlik gerektiren literal yollarla asla çakışmaz.
                        .requestMatchers(HttpMethod.GET, "/api/businesses/{id:\\d+}").permitAll()
                        // Musteri randevu almadan once "bu isletme Pazar acik mi" gibi
                        // sorular sorabilmeli — calisma saatleri ve kapanislar da
                        // herkese acik (degistiren PUT/POST/DELETE degil, sadece GET).
                        .requestMatchers(HttpMethod.GET, "/api/businesses/*/working-hours").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/businesses/*/closures").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/appointments/available-slots").permitAll()
                        // Kapak fotograflari herkese acik bilgi -- isletme listesi/detayi
                        // gibi (bkz. yukarisi). POST /api/businesses/{id}/photo (yukleme)
                        // BILEREK burada yok, genel "/api/**" kuralina dusup authenticated
                        // kaliyor -- OwnershipGuard zaten controller'da kontrol ediyor.
                        .requestMatchers(HttpMethod.GET, "/api/business-photos/**").permitAll()
                        // Swagger UI / OpenAPI semasi (Faz 3.3). Prod'da bu yollara hic
                        // gerek yok -- springdoc.api-docs.enabled/swagger-ui.enabled
                        // application-prod.properties'te false, yollar hic mapping'e
                        // girmiyor. Burada permitAll olmasi sadece dev/test'te API
                        // yuzeyini API'nin KENDISINI kullanmadan (token almadan)
                        // kesfedebilmek icin -- gercek uclara istek atmak icin Swagger
                        // UI'in "Authorize" kilidinden yine gercek bir JWT gerekiyor.
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/**").authenticated())
                // Token yok/geçersizken artık Spring'in varsayılan
                // Http403ForbiddenEntryPoint'i yerine kendi 401 üreten
                // RestAuthenticationEntryPoint'imiz çalışıyor.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
                .httpBasic(httpBasic -> httpBasic.disable())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // PasswordEncoder metodu
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    // Izin verilen origin'ler PROFILE'A GORE degisir: dev'de localhost:*
    // wildcard'i (application-dev.properties), prod'da gercek frontend
    // domain'i (application-prod.properties, env degiskeninden, varsayilansiz
    // -- tanimsizsa uygulama acilmaz). Eskiden bu liste burada sabit
    // kodluydu ("http://localhost:*") -- allowCredentials(true) acikken bu
    // kalibin prod'a sizmasi gercek bir risk olurdu (herhangi bir portta
    // calisan yerel bir sayfa, kurbanin tarayicisinda kimlik bilgileriyle
    // API'ye istek atabilirdi). Artik prod dosyasinda bu deger hic yoksa
    // (env degiskeni bos) uygulama fail-fast ile hic acilmiyor, sessizce
    // yanlis bir varsayilana dusmuyor.
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
