package com.randevu.backend.mapper;

import com.randevu.backend.dto.request.BusinessRequest;
import com.randevu.backend.dto.response.BusinessDetailResponse;
import com.randevu.backend.dto.response.BusinessPhotoResponse;
import com.randevu.backend.dto.response.BusinessResponse;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.BusinessPhoto;
import com.randevu.backend.entity.ServiceItem;
import com.randevu.backend.storage.BusinessPhotoStorage;

import java.util.List;

public final class BusinessMapper {

    private BusinessMapper() {
    }

    // averageRating/reviewCount/coverPhotoKey ve photoStorage BİLEREK
    // parametre — bu sınıf diğer mapper'lar gibi durumsuz (stateless)
    // kalmalı, kendi başına sorgu atmamalı (SRP: mapper veri DÖNÜŞTÜRÜR,
    // veri TOPLAMAZ). Puanı hesaplayıp buraya veren taraf BusinessService.
    //
    // coverPhotoKey V17'den itibaren business.getPhotoKey() (artık yok) YA DA
    // business.getPhotos() (bir @OneToMany -- BİLEREK yok, bkz. BusinessPhoto
    // entity'sindeki gerekçe) DEĞİL, ÇAĞIRAN TARAFTAN geliyor (bkz. NOTLAR.md
    // "N+1" notu). Liste uçlarında (BusinessController) bu, TÜM işletmeler
    // için TEK toplu sorguyla önceden hesaplanmış bir Map'ten okunuyor;
    // mapper'ın kendisi kaç işletme işlendiğinden habersiz, bu yüzden
    // yanlışlıkla N+1 üretemez.
    public static BusinessResponse toResponse(Business business, Double averageRating, long reviewCount,
            String coverPhotoKey, BusinessPhotoStorage photoStorage) {
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
                business.isVerified(),
                cardUrl(coverPhotoKey, photoStorage),
                business.getSuspendedAt() != null,
                business.isAutoApprove());
    }

    // business.getServiceItems() TÜM hizmetleri (soft-delete edilmişler
    // dahil) taşıyor çünkü entity ilişkisi bir filtre uygulamıyor. Müşteriye
    // dönen bu detay görünümünde, silinmiş bir hizmetin görünüp seçilebilir
    // gibi durması yanlış olur — ServiceItemService.getServicesByBusiness'teki
    // aynı kuralı burada da uyguluyoruz.
    //
    // photos TEK bir işletme için önceden sıralı çekilmiş TAM liste (bkz.
    // BusinessPhotoRepository.findByBusinessIdOrderByDisplayOrderAscIdAsc) --
    // burada N+1 riski YOK çünkü bu uç zaten tek bir işletmeyi işliyor, liste
    // uçlarındaki gibi N kez çağrılmıyor. Kapak, listenin ilk elemanı (en
    // küçük display_order) -- ayrıca bir coverPhotoKey parametresi almaya
    // gerek yok, zaten elimizdeki listeden türetilebiliyor.
    public static BusinessDetailResponse toDetailResponse(Business business, Double averageRating, long reviewCount,
            List<BusinessPhoto> photos, BusinessPhotoStorage photoStorage) {
        BusinessPhoto cover = photos.isEmpty() ? null : photos.get(0);
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
                business.isVerified(),
                cardUrl(cover == null ? null : cover.getPhotoKey(), photoStorage),
                detailUrl(cover == null ? null : cover.getPhotoKey(), photoStorage),
                business.getSuspendedAt() != null,
                business.isAutoApprove(),
                photos.stream().map(p -> toPhotoResponse(p, photoStorage)).toList());
    }

    // Public: BusinessController fotoğraf ekleme/silme uçlarında (tek başına,
    // bir Business bağlamı olmadan) doğrudan bunu çağırıyor -- dosya adı
    // türetme kuralının TEK yerde yaşaması için (bkz. aşağıdaki cardUrl/
    // detailUrl yorumu) burada tekrar yazılmadı.
    public static BusinessPhotoResponse toPhotoResponse(BusinessPhoto photo, BusinessPhotoStorage photoStorage) {
        return new BusinessPhotoResponse(
                photo.getId(),
                cardUrl(photo.getPhotoKey(), photoStorage),
                detailUrl(photo.getPhotoKey(), photoStorage),
                photo.getDisplayOrder());
    }

    // Dosya adı türetme kuralı TEK bu iki metotta yaşıyor ("{key}-card.jpg",
    // "{key}-detail.jpg"). key null ise (işletmenin/fotoğrafın karşılığı
    // yoksa) null döner — frontend bu durumda mevcut gradyan kapağı gösterir.
    private static String cardUrl(String photoKey, BusinessPhotoStorage photoStorage) {
        if (photoKey == null) {
            return null;
        }
        return photoStorage.urlFor(photoKey + "-card.jpg");
    }

    private static String detailUrl(String photoKey, BusinessPhotoStorage photoStorage) {
        if (photoKey == null) {
            return null;
        }
        return photoStorage.urlFor(photoKey + "-detail.jpg");
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
                .autoApprove(request.isAutoApprove())
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
        business.setAutoApprove(request.isAutoApprove());
    }
}
