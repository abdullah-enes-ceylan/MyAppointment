package com.randevu.backend.dto.response;

// AppointmentResponse içine gömülü, çok küçük bir işletme özeti. Müşterinin
// "randevularım" ekranında hangi işletmeye gittiğini göstermek için isim
// yeterli — işletmenin tüm detaylarını (adres, saat, hizmet listesi vb.)
// her randevu satırında tekrar taşımaya gerek yok.
//
// suspended (Faz 3.9): doluysa frontend işletme adını tıklanabilir link
// DEĞİL düz metin göstermeli -- backend'in kendi 404'ü (GET /api/businesses/{id})
// zaten gerçek güvenlik sınırı, bu alan sadece kırık bir link gibi
// görünmesin diye.
public record BusinessSummary(Long id, String name, boolean suspended) {
}
