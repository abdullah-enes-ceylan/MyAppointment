package com.randevu.backend.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class BusinessClosureRequest {

    // @FutureOrPresent: geçmişte bir tarihi "kapalıyız" diye işaretlemenin
    // hiçbir anlamı yok — o tarihe zaten randevu alınamaz artık.
    @NotNull(message = "Tarih belirtilmelidir.")
    @FutureOrPresent(message = "Geçmiş bir tarih için kapanış eklenemez.")
    private LocalDate date;

    private String reason;
}
