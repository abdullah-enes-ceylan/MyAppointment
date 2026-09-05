package com.randevu.backend.dto.response;

import com.randevu.backend.entity.BusinessCategory;
import com.randevu.backend.entity.ServedGender;
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
        // Kategoriden ayrı: kategori "ne hizmeti", bu "kime" (bkz. ServedGender).
        ServedGender servedGender,
        List<ServiceItemResponse> serviceItems,
        // Faz 2.7 — bkz. BusinessResponse'daki açıklama.
        Double averageRating,
        long reviewCount,
        // Faz 2.8 — işletme konumunu henüz girmemişse null.
        Double latitude,
        Double longitude,
        // Onaylı işletme rozeti — sadece admin/seed tarafından set edilir.
        boolean verified,
        // Kapak fotoğrafı yüklenmemişse ikisi de null — frontend bu durumda
        // mevcut gradyan kapağı gösterir. BusinessResponse'tan farklı olarak
        // burada İKİSİ de var: bu DTO hem kart hem detay sayfasında kullanılıyor.
        String coverPhotoCardUrl,
        String coverPhotoDetailUrl,
        // Faz 3.9 — bkz. BusinessResponse'daki aynı alanın açıklaması.
        boolean suspended,
        // "Otomatik onay" anahtarı — bkz. BusinessResponse'daki aynı alanın açıklaması.
        boolean autoApprove,
        // V17: TÜM fotoğraflar, sıralı (ilki = kapak, coverPhotoCardUrl ile
        // aynı fotoğraf). Müşteri tarafı carousel'i (PR3) bunu kullanacak;
        // panel Galeri sekmesi de aynı şekilde. Hiç fotoğraf yoksa boş liste
        // (null DEĞİL) — frontend'de "photos.length === 0" kontrolü yeterli.
        List<BusinessPhotoResponse> photos) {
}
