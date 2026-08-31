package com.randevu.backend.notification;

// Bir NotificationPort implementasyonu gonderimi tamamlayamadiginda
// firlatir (I/O hatasi, dis servis coktu vb.). NotificationService bunu
// yakalayip notification_log'a FAILED yazar -- randevunun kendi durumunu
// (ornegin EXPIRED) ETKILEMEZ, bkz. AppointmentNotificationListener'daki
// AFTER_COMMIT gerekcesi.
public class NotificationDeliveryException extends RuntimeException {
    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotificationDeliveryException(String message) {
        super(message);
    }
}
