package com.randevu.backend.dto.response;

// ServiceItem entity'sinin dışa açılan hali. Entity'deki business (FK) ve
// isActive (iç sistem bayrağı) alanları burada bilerek yok — istemcinin
// bunları görmesine hiç gerek yok.
public record ServiceItemResponse(
        Long id,
        String name,
        String description,
        double price,
        int durationInMinutes) {
}
