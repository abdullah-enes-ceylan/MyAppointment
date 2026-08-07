package com.randevu.backend.repository;

import com.randevu.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Veritabanında girilen email'e sahip kullanıcı var mı kontrol etmek ve
    // getirmek için
    Optional<User> findByEmail(String email);
}