package com.randevu.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Devre dışı bırakıyoruz (Basitlik için)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/users/register").permitAll() // Kayıt olmaya herkes erişebilir
                        .requestMatchers(HttpMethod.GET, "/api/businesses").permitAll() // Sadece GET isteğine izin ver
                        .requestMatchers(HttpMethod.GET, "/api/appointments/available-slots")
                        .permitAll() // Frontend testleri bozulmasın diye şimdilik açık
                        .requestMatchers("/api/**").authenticated() // Diğer her şey kimlik doğrulaması gerektirir
                )
                .httpBasic(httpBasic -> httpBasic.disable()); // HTTP Basic Auth'u kapatıyoruz
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // En güçlü hashleme algoritmalarından biri
    }

}
