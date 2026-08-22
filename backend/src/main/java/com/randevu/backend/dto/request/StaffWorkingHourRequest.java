package com.randevu.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

// WorkingHourRequest ile birebir ayni tasarim (bkz. o dosyadaki aciklama),
// personel bazli hali. Alan adi ayni sebeple "closed" ("isClosed" degil).
@Getter
@Setter
public class StaffWorkingHourRequest {

    @NotNull(message = "Gün belirtilmelidir.")
    private DayOfWeek dayOfWeek;

    private LocalTime openTime;
    private LocalTime closeTime;
    private boolean closed;
}
