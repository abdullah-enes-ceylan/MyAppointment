package com.randevu.backend.entity;

// BARBER buradan KALDIRILDI (bkz. V10 migration). Sebebi: "Berber" ile
// "Kuaför" aynı düzlemde iki kategori değildi -- berber sadece erkeğe
// hizmet veren bir kuaför, yani diğerinin alt kümesiydi. Taşıdığı asıl
// bilgi ("burası sadece erkek") silinmedi, ServedGender alanına taşındı;
// oradaki açıklama bu ayrımın neden gerekli olduğunu anlatıyor.
public enum BusinessCategory {
    HAIRDRESSER, // Kuaför (berber ve erkek kuaförü dahil — ayrım ServedGender'da)
    BEAUTY_SALON, // Güzellik Salonu
    SPA_WELLNESS, // Spa ve Masaj
    NAIL_STUDIO, // Tırnak Stüdyosu
    MAKEUP_STUDIO, // Makyaj Stüdyosu
    TATTOO_STUDIO // Dövme Stüdyosu
}
