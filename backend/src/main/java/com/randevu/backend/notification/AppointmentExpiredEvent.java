package com.randevu.backend.notification;

// AppointmentService.expireStaleRequests() tarafindan, bir randevu
// EXPIRED'a gectiginde yayinlanir. Appointment ENTITY'sini degil sadece
// ID'lerini tasiyor -- AFTER_COMMIT dinleyicisi transaction kapandiktan
// SONRA calisiyor, o an artik orijinal persistence context'e bagli bir
// entity'yi tasimak guvenli olmazdi (detached-entity riski).
//
// Bu olay simdilik sadece bildirim icin kullaniliyor ama AppointmentService
// bunu YAYINLIYOR olmasi disinda hicbir sey bilmiyor -- kimin dinledigini,
// hatta dinleyen olup olmadigini bilmiyor. AppointmentService'in
// NotificationPort'u dogrudan bilmesini istemedigimiz icin (bkz.
// AppointmentNotificationListener'daki gerekce) bu ayrim bilincli.
public record AppointmentExpiredEvent(Long appointmentId, Long customerId) {
}
