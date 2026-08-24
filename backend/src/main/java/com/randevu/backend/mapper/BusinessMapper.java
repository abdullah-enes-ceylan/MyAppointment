package com.randevu.backend.mapper;

import com.randevu.backend.dto.request.BusinessRequest;
import com.randevu.backend.dto.response.BusinessDetailResponse;
import com.randevu.backend.dto.response.BusinessResponse;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.ServiceItem;

public final class BusinessMapper {

    private BusinessMapper() {
    }

    // averageRating/reviewCount BİLEREK parametre — bu sınıf diğer
    // mapper'lar gibi durumsuz (stateless) kalmalı, ReviewRepository'ye
    // kendi erişip sorgu atmamalı (SRP: mapper veri DÖNÜŞTÜRÜR, veri
    // TOPLAMAZ). Puanı hesaplayıp buraya veren taraf BusinessService.
    public static BusinessResponse toResponse(Business business, Double averageRating, long reviewCount) {
        return new BusinessResponse(
                business.getId(),
                business.getName(),
                business.getAddress(),
                business.getPhone(),
                business.getDescription(),
                business.getOpenTime(),
                business.getCloseTime(),
                business.getCategory(),
                business.getServedGender(),
                averageRating,
                reviewCount,
                business.getLatitude(),
                business.getLongitude(),
                business.isVerified());
    }

    // business.getServiceItems() TÜM hizmetleri (soft-delete edilmişler
    // dahil) taşıyor çünkü entity ilişkisi bir filtre uygulamıyor. Müşteriye
    // dönen bu detay görünümünde, silinmiş bir hizmetin görünüp seçilebilir
    // gibi durması yanlış olur — ServiceItemService.getServicesByBusiness'teki
    // aynı kuralı burada da uyguluyoruz.
    public static BusinessDetailResponse toDetailResponse(Business business, Double averageRating, long reviewCount) {
        return new BusinessDetailResponse(
                business.getId(),
                business.getName(),
                business.getAddress(),
                business.getPhone(),
                business.getDescription(),
                business.getOpenTime(),
                business.getCloseTime(),
                business.getCategory(),
                business.getServedGender(),
                business.getServiceItems().stream()
                        .filter(ServiceItem::isActive)
                        .map(ServiceItemMapper::toResponse)
                        .toList(),
                averageRating,
                reviewCount,
                business.getLatitude(),
                business.getLongitude(),
                business.isVerified());
    }

    // Yeni işletme oluştururken kullanılıyor. owner ve id burada BİLEREK
    // set edilmiyor — owner'ı çağıran (BusinessService.createBusiness)
    // ayrıca set ediyor, id veritabanı tarafından üretilir. İstemci
    // BusinessRequest'te bu alanları hiç göndermediği için mass
    // assignment riski yok.
    public static Business toEntity(BusinessRequest request) {
        return Business.builder()
                .name(request.getName())
                .address(request.getAddress())
                .phone(request.getPhone())
                .description(request.getDescription())
                .openTime(request.getOpenTime())
                .closeTime(request.getCloseTime())
                .category(request.getCategory())
                .servedGender(request.getServedGender())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();
    }

    // Var olan bir işletmeyi günceller. ServiceItemService.updateService'teki
    // desenle aynı: alanlar TEK TEK, açıkça kopyalanıyor — owner ve id'ye
    // hiç dokunulmuyor, istemci bunları güncelleme isteğinde göndermeye
    // çalışsa bile (BusinessRequest'te zaten yer almadıkları için) etkisiz.
    public static void applyToEntity(BusinessRequest request, Business business) {
        business.setName(request.getName());
        business.setAddress(request.getAddress());
        business.setPhone(request.getPhone());
        business.setDescription(request.getDescription());
        business.setOpenTime(request.getOpenTime());
        business.setCloseTime(request.getCloseTime());
        business.setCategory(request.getCategory());
        business.setServedGender(request.getServedGender());
        business.setLatitude(request.getLatitude());
        business.setLongitude(request.getLongitude());
    }
}
