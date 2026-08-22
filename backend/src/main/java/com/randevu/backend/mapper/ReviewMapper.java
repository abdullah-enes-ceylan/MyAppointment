package com.randevu.backend.mapper;

import com.randevu.backend.dto.response.ReviewResponse;
import com.randevu.backend.dto.response.ReviewerSummary;
import com.randevu.backend.entity.Review;

public final class ReviewMapper {

    private ReviewMapper() {
    }

    public static ReviewResponse toResponse(Review review) {
        var customer = review.getAppointment().getCustomer();
        return new ReviewResponse(
                review.getId(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt(),
                new ReviewerSummary(customer.getName(), customer.getSurName()));
    }
}
