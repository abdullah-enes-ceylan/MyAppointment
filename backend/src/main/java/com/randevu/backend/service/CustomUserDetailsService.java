package com.randevu.backend.service;

import com.randevu.backend.entity.User;
import com.randevu.backend.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // 1. Kullanıcıyı kendi repository'mizden email ile buluyoruz
        User user = userRepository.findByEmail(email)
                .orElseThrow(
                        () -> new UsernameNotFoundException("Bu email ile kayıtlı kullanıcı bulunamadı: " + email));

        // 2. Kendi Role enum'ımızı Spring Security'nin anladığı GrantedAuthority
        // formatına çeviriyoruz
        // Not: Spring Security standart olarak rollerin başında "ROLE_" ön ekini
        // görmeyi sever.
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

        // 3. Bizim User'ı Spring Security'nin UserDetails nesnesine paketleyip sisteme
        // teslim ediyoruz
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                Collections.singletonList(authority));
    }
}
