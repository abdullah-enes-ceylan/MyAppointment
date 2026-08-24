package com.randevu.backend.dto.response;

// Profil sayfasindaki ozet sayilar. Hepsi GERCEK veriden COUNT sorgusuyla
// geliyor -- uydurma/sabit deger yok. Ayri bir uc noktada (GET
// /api/users/me/stats) tutuluyor cunku profil bilgisinin kendisi (ad,
// e-posta) her sayfa acilisinda lazim, sayilar ise sadece bu ekranda;
// ikisini tek yanita gomup her /me cagrisinda 5 COUNT sorgusu attirmak
// gereksiz olurdu.
public record ProfileStatsResponse(
        long totalAppointments,
        long completedAppointments,
        long upcomingAppointments,
        long favoriteCount,
        long reviewCount) {
}
