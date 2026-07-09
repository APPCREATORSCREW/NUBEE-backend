package com.solux31.nubee_BE.domain.review.controller;

import com.solux31.nubee_BE.domain.review.dto.ReviewResDTO;
import com.solux31.nubee_BE.domain.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @GetMapping("/history")
    public ResponseEntity<ReviewResDTO.ReviewResponse> getReviewNews(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = authUser.getUserId();
        ReviewResDTO.ReviewResponse response =
                reviewService.getReviewNews(userId, category, page, size);
        return ResponseEntity.ok(response);
    }
}
