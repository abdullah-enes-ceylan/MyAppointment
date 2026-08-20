package com.randevu.backend.dto.response;

import java.time.Instant;
import java.util.Map;

// ErrorResponse'un doğrulama hatalarına özel hali. Farkı: hangi ALANIN
// neden geçersiz olduğunu (fieldErrors) da taşıması — frontend bu sayede
// tek bir genel mesaj yerine, hatalı olan form alanının altına spesifik
// hatayı gösterebilir (örn. "password: Şifre en az 8 karakter olmalı.").
public record ValidationErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {
}
