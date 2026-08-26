package com.randevu.backend.mapper;

import com.randevu.backend.dto.request.ServiceItemRequest;
import com.randevu.backend.dto.response.ServiceItemResponse;
import com.randevu.backend.entity.ServiceItem;

// Manuel mapper — MapStruct gibi bir kütüphane bu ölçekte (birkaç DTO,
// basit alan eşlemesi) gereksiz bir bağımlılık olurdu. Static metotlar:
// bu sınıfın state'i yok, tek işi entity -> DTO dönüşümü, Spring bean'i
// olmasına gerek yok.
public final class ServiceItemMapper {

    private ServiceItemMapper() {
    }

    public static ServiceItemResponse toResponse(ServiceItem serviceItem) {
        return new ServiceItemResponse(
                serviceItem.getId(),
                serviceItem.getName(),
                serviceItem.getDescription(),
                serviceItem.getPrice(),
                serviceItem.getDurationInMinutes());
    }

    // Yeni hizmet olustururken. id, business ve isActive burada BILEREK
    // set edilmiyor: id'yi veritabani uretir, business'i cagiran
    // (ServiceItemService.createServiceItem) path'teki businessId'den
    // yukleyip set eder, isActive entity'de @Builder.Default ile true
    // baslar. Istemci bu ucunu hicbirine dokunamaz cunku ServiceItemRequest'te
    // bu alanlar hic yok (bkz. o DTO'daki aciklama -- eskiden ham entity
    // baglaniyordu ve govdeye konan bir "id" ile baska bir isletmenin
    // hizmeti ele geciriliyordu).
    public static ServiceItem toEntity(ServiceItemRequest request) {
        return ServiceItem.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .durationInMinutes(request.getDurationInMinutes())
                .build();
    }

    // Var olan bir hizmeti gunceller. BusinessMapper.applyToEntity ile ayni
    // desen: alanlar TEK TEK, acikca kopyalaniyor; id, business ve isActive'e
    // hic dokunulmuyor. Guncellenecek entity DAIMA veritabanindan yuklenmis
    // olan -- istemciden gelen bir nesne degil.
    public static void applyToEntity(ServiceItemRequest request, ServiceItem serviceItem) {
        serviceItem.setName(request.getName());
        serviceItem.setDescription(request.getDescription());
        serviceItem.setPrice(request.getPrice());
        serviceItem.setDurationInMinutes(request.getDurationInMinutes());
    }
}
