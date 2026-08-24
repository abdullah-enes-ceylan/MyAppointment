package com.randevu.backend.entity;

// İşletmenin kime hizmet verdiği. Kategoriden AYRI bir eksen olarak
// duruyor çünkü ikisi bağımsız sorular: kategori "ne hizmeti"
// (saç/güzellik/spa...), bu alan "kime" cevabını veriyor.
//
// Eskiden bu bilgi kategorinin İÇİNE gömülüydü: BARBER = erkek,
// HAIRDRESSER = belirsiz. Bu üç şeyi birden bozuyordu:
//  1. Unisex bir salon ikisinden birini seçmek zorunda kalıp
//     müşterisinin yarısına görünmez oluyordu (unisex'i ifade etmenin
//     hiçbir yolu yoktu).
//  2. "Erkek kuaförü" diyen bir işletme hangisini seçeceğini bilemiyordu.
//  3. Erkek saç kesimi arayan müşteri iki kategoriyi ayrı gezmek
//     zorundaydı, üstelik kadın kuaförüne yanlışlıkla randevu isteği
//     gönderebiliyordu (işletmenin kutusuna alakasız talep düşüyordu).
public enum ServedGender {
    MALE, // Sadece erkek (geleneksel berber, erkek kuaförü)
    FEMALE, // Sadece kadın
    UNISEX // Her ikisi
}
