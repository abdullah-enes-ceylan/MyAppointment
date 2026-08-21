package com.randevu.backend.dto.response;

import java.time.DayOfWeek;
import java.time.LocalTime;

// Alan adı bilerek "closed" — WorkingHourRequest'teki isimle tutarlı olsun
// diye (o alan Lombok'un "is" önek stripping'i yüzünden mecburen "closed"
// oldu; record'da bu sorun olmasa da, istemcinin gönderirken/alırken aynı
// adı görmesi karışıklığı önlüyor).
public record WorkingHourResponse(
        DayOfWeek dayOfWeek,
        LocalTime openTime,
        LocalTime closeTime,
        boolean closed) {
}
