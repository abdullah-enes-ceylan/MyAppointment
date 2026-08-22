package com.randevu.backend.entity;

public enum AppointmentStatus {
    PENDING, // Onay bekliyor
    APPROVED, // Onaylandı
    REJECTED, // İşletme reddetti
    CANCELLED, // Müşteri veya işletme iptal etti
    COMPLETED, // Randevu gerçekleşti ve bitti (Faz 2.2'deki otomatik job ile)
    NO_SHOW // Onaylandı ama müşteri gelmedi — işletme sahibi manuel işaretler
}