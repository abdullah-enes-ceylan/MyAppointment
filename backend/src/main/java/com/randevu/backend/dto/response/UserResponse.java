package com.randevu.backend.dto.response;

import com.randevu.backend.entity.Role;

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
        Role role) {
}
