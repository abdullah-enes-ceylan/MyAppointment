package com.randevu.backend.config;

// Isletme kapak fotografinin hangi depolama arka ucuna yazilacagini secer
// (bkz. NOTLAR.md "R2'ye tasima" karari, BusinessPhotoStorage arayuzu).
//
// BILEREK enum -- String degil. Gecersiz bir deger (ornekle prod'da yanlislikla
// "R2 " ya da "r2local" yazilirsa) Spring'in kendi binding mekanizmasi acilista
// hata verip uygulamayi DURDURUR. Bu, AppointmentPolicyProperties'teki "gecersiz
// degerde guvenli varsayilana dus + uyari logla" desenin BILEREK DISINDA: o desen
// sayisal/aralik degerleri icin dogru (yanlis bir dakika degeri veri kaybettirmez),
// ama burada yanlis bir deger sessizce "local"e duserse prod fotograflari
// container'in gecici diskine yazilmaya baslar -- CLAUDE.md'nin "kalici disk
// riski" diye kayit altina aldigi senaryonun ta kendisi, farkedilmeden.
public enum BusinessPhotoStorageProvider {
    LOCAL,
    R2
}
