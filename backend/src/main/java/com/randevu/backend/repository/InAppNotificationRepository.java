package com.randevu.backend.repository;

import com.randevu.backend.entity.InAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {
    // Ileride GET /api/notifications/me icin -- su an sadece testlerde
    // "gercekten dogru kullaniciya mi gitti" dogrulamasi icin kullaniliyor.
    List<InAppNotification> findByRecipientUserId(Long recipientUserId);
}
