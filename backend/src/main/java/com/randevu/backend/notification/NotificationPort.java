package com.randevu.backend.notification;

// Kanaldan bagimsiz gonderim arayuzu (Faz 3.4, bkz. CLAUDE.md karar
// tablosu "Bildirim kanali: karar ertelendi"). Kanal secildiginde tek bir
// yeni implementasyon eklenip Spring bean'i olarak bagli olan implementasyon
// degistirilir -- bu arayuzu ya da onu cagiran NotificationService'i cagiran
// hicbir kod degismez.
public interface NotificationPort {

    // Basarisiz olursa NotificationDeliveryException firlatir -- basarili/
    // basarisiz durumunu bir boolean/kod yerine exception ile bildirmesinin
    // sebebi: cagiran taraf (NotificationService) zaten try/catch ile
    // SENT/FAILED ayrimini yapiyor, bu Java'nin dogal hata bildirme yolu.
    void send(Notification notification);
}
