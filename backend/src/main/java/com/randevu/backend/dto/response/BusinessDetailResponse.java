package com.randevu.backend.dto.response;

import com.randevu.backend.entity.BusinessCategory;
import java.time.LocalTime;
import java.util.List;

// BusinessResponse'un hizmetler gömülü hali. Frontend şu an tek bir uçtan
// (GET /api/businesses) hem liste hem detay ihtiyacını karşılıyor
// (BusinessDetailPage.jsx, dedicated GET /businesses/{id} gelene kadar —
// Faz 1.5 — tüm listeyi çekip client'ta filtreliyor), o yüzden bu şekil
// şimdilik oradaki uçlarda kullanılıyor. Owner bilgisi burada da yok.
public record BusinessDetailResponse(
        Long id,
        String name,
        String address,
        String phone,
        String description,
        LocalTime openTime,
        LocalTime closeTime,
        BusinessCategory category,
        List<ServiceItemResponse> serviceItems,
        // Faz 2.7 — bkz. BusinessResponse'daki açıklama.
        Double averageRating,
        long reviewCount) {
}
