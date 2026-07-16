package com.solux31.nubee_BE.domain.review.controller;

import com.solux31.nubee_BE.domain.review.dto.ReviewResDTO;
import com.solux31.nubee_BE.domain.review.exception.code.ReviewSuccessCode;
import com.solux31.nubee_BE.domain.review.service.ReviewService;
import com.solux31.nubee_BE.global.apiPayload.ApiResponse;
import com.solux31.nubee_BE.global.security.entity.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "복습", description = "뉴스 다시보기 관련 API")
@SecurityRequirement(name = "JWT TOKEN")
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviewService;

    @Operation(
            summary = "뉴스 다시보기 목록 조회",
            description = "로그인한 유저가 이전에 조회했던 뉴스 기록을 카테고리별로 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "뉴스 다시보기 목록 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패 (토큰 없음 또는 만료)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<ReviewResDTO.ReviewResponse>> getReviewNews(
            @AuthenticationPrincipal AuthUser authUser,
            @Parameter(description = "카테고리 필터 (필수)", required = true) @RequestParam String category,
            @Parameter(description = "페이지 번호 (기본값 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지당 개수 (기본값 20)") @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = authUser.getUserId();
        ReviewResDTO.ReviewResponse response =
                reviewService.getReviewNews(userId, category, page, size);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(ReviewSuccessCode.REVIEW_FETCH_SUCCESS, response)
        );
    }
}
