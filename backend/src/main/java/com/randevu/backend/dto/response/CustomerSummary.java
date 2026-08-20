package com.randevu.backend.dto.response;

// AppointmentResponse içine gömülü, PII'si en aza indirilmiş müşteri özeti.
// İşletme sahibinin "kim geliyor" diye ad/telefon görmesi meşru (randevuyu
// yönetmek için gerekli), ama email'i ve rolü görmesine hiç gerek yok —
// bu yüzden UserResponse'un TAMAMI değil, bilerek daha dar bir alt kümesi.
public record CustomerSummary(Long id, String name, String surName, String phone) {
}
