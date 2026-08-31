package com.randevu.backend.entity;

// SENT: gonderim gercekten basarili oldu -- idempotency'nin TEK kaynagi
// (bkz. notification_log'daki partial unique index). FAILED: denendi ama
// basarisiz oldu, hicbir seyi ENGELLEMEZ -- ayni randevu bir sonraki
// taramada SENT satiri olmadigi icin otomatik yeniden denenir. FAILED
// satirlari sadece "neden gitmedi" sorusuna cevap vermek icin tutuluyor.
public enum NotificationStatus {
    SENT,
    FAILED
}
