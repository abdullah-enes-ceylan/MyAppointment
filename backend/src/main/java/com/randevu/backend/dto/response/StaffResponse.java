package com.randevu.backend.dto.response;

import java.util.List;

// Staff entity'sinin disa acilan hali. isActive bilerek yok -- ServiceItemResponse'daki
// gibi, bu DTO'yu donduren sorgular zaten sadece aktif personeli getiriyor
// (bkz. StaffRepository.findByBusinessIdAndIsActiveTrue), yani alan hep
// true olurdu.
public record StaffResponse(
        Long id,
        String name,
        List<ServiceItemResponse> services) {
}
