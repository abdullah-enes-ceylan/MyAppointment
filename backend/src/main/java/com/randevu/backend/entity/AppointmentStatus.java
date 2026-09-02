package com.randevu.backend.entity;

import java.util.List;

public enum AppointmentStatus {
    PENDING, // Onay bekliyor
    APPROVED, // Onaylandı
    REJECTED, // İşletme reddetti
    CANCELLED, // Müşteri veya işletme iptal etti
    COMPLETED, // Randevu gerçekleşti ve bitti
    NO_SHOW, // Onaylandı ama müşteri gelmedi — işletme sahibi manuel işaretler

    // İşletme talebi hiç cevaplamadı ve süre doldu. REJECTED'dan ayrı bir
    // durum: "işletme seni reddetti" demek yanlış bilgi olurdu, işletme
    // sadece görmemiş. Sadece zamanlanmış görev atar, elle geçilemez
    // (bkz. AppointmentService.changeStatus — hiçbir eylemin girdisi değil,
    // dolayısıyla terminal). Düşme anı: AppointmentExpiryPolicy.
    EXPIRED;

    // "Yaklaşan/aktif" randevu = henüz sonuçlanmamış (PENDING/APPROVED).
    // "Yaklaşan randevu" kavramını kullanan HER yer (profil özeti,
    // müşterinin ve işletmenin yaklaşan randevu listesi) tek bu listeden
    // okur — ayrı kopyalar aynı tutarsızlığı üçüncü bir çağrı noktasında
    // geri getirir (bkz. ProfileStatsService, AppointmentService
    // getUpcomingCustomerAppointments/getUpcomingBusinessAppointments).
    public static final List<AppointmentStatus> ACTIVE_STATUSES = List.of(PENDING, APPROVED);
}