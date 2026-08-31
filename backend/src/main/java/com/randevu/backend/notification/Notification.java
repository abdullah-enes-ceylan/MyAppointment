package com.randevu.backend.notification;

import com.randevu.backend.entity.NotificationType;

// NotificationPort'un tasidigi tek veri birimi. Bilerek Appointment/Business
// gibi domain nesnelerini ICERMIYOR -- mesaj metni (title/body) cagiran
// tarafta (bkz. NotificationService) ONCEDEN uretiliyor. Boylece bir SMS/
// e-posta adapter'i yazildiginda o da Appointment'i hic bilmek zorunda
// kalmaz, sadece "bu metni bu kullaniciya ilet" der -- kanal bagimsizligi
// bu ayrimla korunuyor.
public record Notification(
        Long recipientUserId,
        NotificationType type,
        String title,
        String body) {
}
