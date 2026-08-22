package com.randevu.backend.dto.response;

import java.time.DayOfWeek;
import java.time.LocalTime;

// WorkingHourResponse'un personel bazli esdegeri.
public record StaffWorkingHourResponse(
        DayOfWeek dayOfWeek,
        LocalTime openTime,
        LocalTime closeTime,
        boolean closed) {
}
