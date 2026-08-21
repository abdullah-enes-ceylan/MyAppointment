package com.randevu.backend.dto.response;

import java.time.LocalDate;

public record BusinessClosureResponse(Long id, LocalDate date, String reason) {
}
