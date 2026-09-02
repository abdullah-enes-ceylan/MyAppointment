package com.randevu.backend.dto.response;

import com.randevu.backend.entity.BusinessCategory;
import com.randevu.backend.entity.ServedGender;
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
        // Kategoriden ayrı: kategori "ne hizmeti", bu "kime" (bkz. ServedGender).
        ServedGender servedGender,
        // Faz 2.7 — hiç yorum yoksa null (SQL AVG() boş küme için null döner,
        // "puan yok" ile "puan 0" birbirine karışmasın diye 0 değil null).
        Double averageRating,
        long reviewCount,
        // Faz 2.8 — işletme konumunu henüz girmemişse null.
        Double latitude,
        Double longitude,
        // Onaylı işletme rozeti — sadece admin/seed tarafından set edilir.
        boolean verified,
        // Kapak fotoğrafı yüklenmemişse null — frontend bu durumda mevcut
        // gradyan kapağı gösterir. Sadece kart boyutu: bu DTO liste
        // görünümlerinde kullanılıyor, detay boyutuna ihtiyaç yok
        // (bkz. BusinessDetailResponse.coverPhotoDetailUrl).
        String coverPhotoCardUrl,
        // Faz 3.9: sahibi hesap silme talep etmiş mi. Bu DTO'yu kullanan
        // uçların hepsi zaten suspendedAt IS NULL filtreli listeler DÖNDÜĞÜ
        // için (bkz. BusinessService.getAllBusinesses/getBusinessesByCategory)
        // burada normalde hep false görünür — bu alan asıl işlevini işletme
        // sahibinin KENDİ panelinde (findByOwnerId, BİLEREK filtresiz) görür:
        // frontend panelin salt-okunur banner'ını bu alana bakarak gösterir.
        boolean suspended) {
}
