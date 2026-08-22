package com.randevu.backend.dto.response;

// AppointmentResponse içine gömülü, çok küçük bir personel özeti —
// BusinessSummary ile aynı gerekçe. null olabilir: randevu personel
// atanmadan oluşturulmuşsa (bkz. Faz 2.5 — personel sistemi kullanmayan
// işletmeler için hâlâ geçerli, varsayılan akış).
public record StaffSummary(Long id, String name) {
}
