package com.randevu.backend.controller;

import com.randevu.backend.dto.request.ReviewRequest;
import com.randevu.backend.dto.response.ReviewResponse;
import com.randevu.backend.entity.Review;
import com.randevu.backend.entity.User;
import com.randevu.backend.mapper.ReviewMapper;
import com.randevu.backend.service.CurrentUserService;
import com.randevu.backend.service.ReviewService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUserService currentUserService;

    public ReviewController(ReviewService reviewService, CurrentUserService currentUserService) {
        this.reviewService = reviewService;
        this.currentUserService = currentUserService;
    }

    // Kimlik (musteri) her zamanki gibi token'dan -- appointmentId hangi
    // randevuya yorum yapildigini soyluyor, ReviewService o randevunun
    // GERCEKTEN bu kullaniciya ait oldugunu ayrica dogruluyor.
    @PostMapping("/create")
    public ResponseEntity<ReviewResponse> createReview(@Valid @RequestBody ReviewRequest request,
            Authentication authentication) {
        User currentUser = currentUserService.getCurrentUser(authentication);
        Review created = reviewService.createReview(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReviewMapper.toResponse(created));
    }

    @GetMapping("/business/{businessId}")
    public List<ReviewResponse> getReviewsByBusiness(@PathVariable Long businessId) {
        return reviewService.getReviewsForBusiness(businessId).stream()
                .map(ReviewMapper::toResponse)
                .toList();
    }
}
