package com.randevu.backend.dto.response;

// ReviewResponse içine gömülü, PII'si en aza indirilmiş yorumcu özeti.
// CustomerSummary'den FARKLI ve bilerek DAHA DAR: reviews herkese açık
// (bkz. ReviewController — GET işletme sahibi olmayan herkes görebilir),
// bu yüzden telefon numarası KESİNLİKLE burada yok. CustomerSummary'nin
// telefonu görmesi meşruydu çünkü sadece o randevunun işletme sahibine
// dönüyordu; burada muhatap TÜM ziyaretçiler.
public record ReviewerSummary(String name, String surName) {
}
