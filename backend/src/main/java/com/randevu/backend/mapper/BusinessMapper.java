package com.randevu.backend.mapper;

import com.randevu.backend.dto.response.BusinessDetailResponse;
import com.randevu.backend.dto.response.BusinessResponse;
import com.randevu.backend.entity.Business;
import com.randevu.backend.entity.ServiceItem;

public final class BusinessMapper {

    private BusinessMapper() {
    }

    public static BusinessResponse toResponse(Business business) {
        return new BusinessResponse(
                business.getId(),
                business.getName(),
                business.getAddress(),
                business.getPhone(),
                business.getDescription(),
                business.getOpenTime(),
                business.getCloseTime(),
                business.getCategory());
    }

    // business.getServiceItems() TÜM hizmetleri (soft-delete edilmişler
    // dahil) taşıyor çünkü entity ilişkisi bir filtre uygulamıyor. Müşteriye
    // dönen bu detay görünümünde, silinmiş bir hizmetin görünüp seçilebilir
    // gibi durması yanlış olur — ServiceItemService.getServicesByBusiness'teki
    // aynı kuralı burada da uyguluyoruz.
    public static BusinessDetailResponse toDetailResponse(Business business) {
        return new BusinessDetailResponse(
                business.getId(),
                business.getName(),
                business.getAddress(),
                business.getPhone(),
                business.getDescription(),
                business.getOpenTime(),
                business.getCloseTime(),
                business.getCategory(),
                business.getServiceItems().stream()
                        .filter(ServiceItem::isActive)
                        .map(ServiceItemMapper::toResponse)
                        .toList());
    }
}
