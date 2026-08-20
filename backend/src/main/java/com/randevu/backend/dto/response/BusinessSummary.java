package com.randevu.backend.dto.response;

// AppointmentResponse içine gömülü, çok küçük bir işletme özeti. Müşterinin
// "randevularım" ekranında hangi işletmeye gittiğini göstermek için isim
// yeterli — işletmenin tüm detaylarını (adres, saat, hizmet listesi vb.)
// her randevu satırında tekrar taşımaya gerek yok.
public record BusinessSummary(Long id, String name) {
}
