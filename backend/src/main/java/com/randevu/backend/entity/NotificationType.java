package com.randevu.backend.entity;

// Hem notification_log'un (idempotency anahtarinin bir parcasi) hem
// NotificationPort'un (Notification record'unun bir alani) kullandigi
// ortak tur. entity paketinde duruyor cunku NotificationLog bunu bir
// DB kolonu olarak tasiyor -- AppointmentStatus/ServedGender ile ayni
// yerlesim gerekcesi.
public enum NotificationType {
    // Musteriye: bir talep cevaplanmadigi icin dustu (bkz.
    // AppointmentService.expireStaleRequests). Aksiyona donuk: "baska
    // saat secebilirsiniz".
    APPOINTMENT_EXPIRED,

    // Musteriye: onaylanmis randevusu yaklasiyor (varsayilan 24 saat
    // once). ROADMAP'in orijinal Faz 3.4 maddesi -- asil bildirim
    // ozelligi bu, PENDING_EXPIRY_WARNING ve APPOINTMENT_EXPIRED sonradan
    // eklendi. bkz. AppointmentReminderService.
    APPOINTMENT_REMINDER,

    // Isletme sahibine: bekleyen bir talep az sonra dusecek, cevaplamasi
    // icin. AppointmentExpiryPolicy.expiresAt'e gore zamanlaniyor (bkz.
    // AppointmentReminderService) -- musteriye DEGIL isletmeye gidiyor
    // cunku dusmeyi engelleyecek aksiyonu (onay/red) sadece isletme
    // alabilir.
    PENDING_EXPIRY_WARNING
}
