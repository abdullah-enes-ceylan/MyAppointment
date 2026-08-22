package com.randevu.backend.dto.response;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        int rating,
        String comment,
        LocalDateTime createdAt,
        ReviewerSummary reviewer) {
}
