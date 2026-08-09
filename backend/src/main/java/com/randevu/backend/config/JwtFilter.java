package com.randevu.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Her gelen isteği kontrol edecek Kapı Memuru
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Token var mı? (Header'da "Authorization: Bearer ...")
        final String authorizationHeader = request.getHeader("Authorization");

        String email = null; // username yerine projemize uygun olarak email kullanıyoruz
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7); // "Bearer " kısmını silip attık
            try {
                // 2. Token'dan email'i çıkar
                email = jwtUtil.extractEmail(jwt);
            } catch (Exception e) {
                System.out.println("Token çözülemedi veya süresi dolmuş!");
            }
        }

        // 3. Email varsa ve o an oturum açmamışsa sistemi kontrol et
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Veritabanından kullanıcıyı bul
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(email);

            // 4. Token geçerli mi?
            if (jwtUtil.validateToken(jwt, userDetails)) {
                // Geçerli! Oturumu açıyoruz
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // Zincirin devamına izin ver (Uygulama çalışmaya devam etsin)
        filterChain.doFilter(request, response);
    }
}