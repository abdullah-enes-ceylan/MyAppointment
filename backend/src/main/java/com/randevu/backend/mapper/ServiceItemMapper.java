package com.randevu.backend.mapper;

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
}
