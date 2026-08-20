package com.randevu.backend.dto.response;

import java.time.Instant;

// Her hata yanitinin ayni sekilde donmesini saglayan tek tip govde.
// Bilerek stack trace veya exception sinif adi gibi ic detay tasimaz —
// bunlar sunucu logunda kalir, istemciye asla gitmez.
// Record kullanildi: bu sinif salt veri tasiyici, degistirilebilir alanlara
// (setter'a) hic ihtiyaci yok, o yuzden Lombok @Getter/@Setter yerine
// immutable bir record daha dogru secim.
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path) {
}
