package com.randevu.backend.dto.response;

// LocationService.findNearby sonucunun dışa açılan hali. Mevcut
// BusinessResponse'u SARMALIYOR (distanceKm hiçbir yerde tekrar
// tanımlanmadan) -- distanceKm sadece BU sorgu bağlamında anlamlı bir
// alan, normal BusinessResponse'a eklenseydi diğer tüm uçlarda hep
// null/anlamsız dururdu.
public record NearbyBusinessResponse(BusinessResponse business, double distanceKm) {
}
