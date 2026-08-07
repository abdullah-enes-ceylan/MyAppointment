package com.randevu.backend.entity;

public enum AppointmentStatus {
    PENDING, // Onay bekliyor
    APPROVED, // Onaylandı
    REJECTED, // İşletme reddetti
    CANCELLED, // Müşteri iptal etti
    COMPLETED // Randevu gerçekleşti ve bitti
}