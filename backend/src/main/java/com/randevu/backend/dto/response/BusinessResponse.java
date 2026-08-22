package com.randevu.backend.dto.response;

import com.randevu.backend.entity.BusinessCategory;
import java.time.LocalTime;

// Hafif liste görünümü — hizmetler gömülü değil. Business entity'sinden
// farkı: "owner" alanı (sahibin email/telefon/rolü) buraya HİÇ taşınmıyor.
// Eskiden GET /api/businesses (herkese açık, permitAll) bu bilgiyi
// çıplak sızdırıyordu çünkü entity doğrudan JSON'a çevriliyordu.
public record BusinessResponse(
        Long id,
        String name,
        String address,
        String phone,
        String description,
        LocalTime openTime,
        LocalTime closeTime,
        BusinessCategory category,
        // Faz 2.7 — hiç yorum yoksa null (SQL AVG() boş küme için null döner,
        // "puan yok" ile "puan 0" birbirine karışmasın diye 0 değil null).
        Double averageRating,
        long reviewCount) {
}
