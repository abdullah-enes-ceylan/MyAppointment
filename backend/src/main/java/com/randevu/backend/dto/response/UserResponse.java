package com.randevu.backend.dto.response;

import com.randevu.backend.entity.Role;

import java.time.LocalDateTime;

// User entity'sinin dışa açılan hali. password alanı entity'de zaten
// @JsonProperty(WRITE_ONLY) ile korunuyordu, ama entity'nin KENDİSİNİ
// dönmek yine de yanlış — DB şeması ile API sözleşmesi ayrı şeyler
// olmalı (bkz. RegisterRequest'teki mass-assignment tartışması, Faz 0.2).
public record UserResponse(
        Long id,
        String name,
        String surName,
        String email,
        String phone,
        Role role,
        // Faz 3.9: hesap silme talebi. null = talep yok. Frontend'in "hesabınız
        // silinecek" banner'ını göstermesi/gizlemesi için TEK kaynak bu alan —
        // ham deletionRequestedAt'i döndürmek yerine iki alt satırdaki HAZIR
        // eşik değerlerini dönüyoruz ki gracePeriod/businessReversalWindow
        // config'te değişirse frontend'in hiçbir yerde 30 gün/48 saat gibi bir
        // sayıyı tekrar üretmesi gerekmesin (bkz. AppointmentResponse.expiresAt
        // ile aynı "kural sunucuda hesaplanır" gerekçesi).
        LocalDateTime deletionRequestedAt,
        // deletionRequestedAt + gracePeriod. USER ve BUSINESS_OWNER PAYLAŞIYOR
        // (kimlik anonimleştirmesi ikisi için de aynı süre). null = talep yok.
        LocalDateTime identityAnonymizationDeadlineAt,
        // deletionRequestedAt + businessReversalWindow. SADECE BUSINESS_OWNER
        // için dolu (talebi bu tarihe kadar geri almazsa kalan tüm randevular
        // topluca iptal edilir) — USER'da ve talep yoksa null.
        LocalDateTime businessReversalDeadlineAt) {
}
