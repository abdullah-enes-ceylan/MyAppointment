package com.randevu.backend.repository;

import com.randevu.backend.entity.Role;
import com.randevu.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Veritabanında girilen email'e sahip kullanıcı var mı kontrol etmek ve
    // getirmek için
    Optional<User> findByEmail(String email);

    // --- Hesap silme akisi (Faz 3.9) ---

    // BUSINESS_OWNER'in geri donus penceresi (businessReversalWindow) dolmus
    // ama talebi hala aktif -- kalan tum randevularini topluca iptal etmek
    // icin (bkz. AccountDeletionScheduler). cutoff = now - reversalWindow;
    // deletionRequestedAt <= cutoff, yani talep uzerinden en az reversalWindow
    // kadar zaman gecmis demek.
    List<User> findByRoleAndDeletionRequestedAtIsNotNullAndAnonymizedAtIsNullAndDeletionRequestedAtLessThanEqual(
            Role role, LocalDateTime cutoff);

    // Kimlik anonimlestirmesi icin gracePeriod'u dolmus, HENUZ anonimlestirilmemis
    // kullanicilar -- USER ve BUSINESS_OWNER PAYLASIYOR (bkz. ROADMAP 3.9).
    List<User> findByDeletionRequestedAtIsNotNullAndAnonymizedAtIsNullAndDeletionRequestedAtLessThanEqual(
            LocalDateTime cutoff);
}