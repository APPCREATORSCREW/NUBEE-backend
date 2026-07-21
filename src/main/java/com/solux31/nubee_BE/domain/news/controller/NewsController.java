package com.solux31.nubee_BE.domain.news.controller;

import com.solux31.nubee_BE.domain.news.dto.Request.QuizSubmitReqDTO;
import com.solux31.nubee_BE.domain.news.dto.Response.NewsDetailResDTO;
import com.solux31.nubee_BE.domain.news.dto.Response.NewsResDTO;
import com.solux31.nubee_BE.domain.news.dto.Response.QuizResDTO;
import com.solux31.nubee_BE.domain.news.dto.Response.QuizSubmitResDTO;
import com.solux31.nubee_BE.domain.news.exception.code.NewsSuccessCode;
import com.solux31.nubee_BE.domain.news.service.NewsService;
import com.solux31.nubee_BE.global.apiPayload.ApiResponse;
import com.solux31.nubee_BE.global.apiPayload.code.GeneralSuccessCode;
import com.solux31.nubee_BE.global.security.entity.AuthUser; // AuthUser 임포트 추가
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 어노테이션 임포트 추가
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "News API", description = "오늘의 뉴스 리스트, 상세 정보, 뉴스 및 키워드 독해 퀴즈 관련 API")
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    /**
     * [명세서 반영] 오늘의 뉴스 상세 정보 조회
     * GET /api/news/{news_id}
     */
    @Operation(summary = "특정 뉴스 상세 조회",
            description = "뉴스 기사 본문 요약, 원문 출처 링크, 그리고 해당 뉴스에 태깅된 주요 어휘 키워드 리스트를 일괄 반환합니다.")
    @GetMapping("/{news_id}")
    public ApiResponse<NewsDetailResDTO> getNewsDetail(
            @PathVariable("news_id") Long newsId
    ) {
        NewsDetailResDTO responseData = newsService.getNewsDetailWithKeywords(newsId);
        return ApiResponse.onSuccess(NewsSuccessCode.TODAY_NEWS_SUMMARY_GET_SUCCESS, responseData);
    }

    /**
     * [명세서 반영] 특정 뉴스 독해 퀴즈 조회
     * GET /api/news/{news_id}/quiz
     */
    @Operation(summary = "특정 뉴스의 퀴즈 조회",
            description = "뉴스 ID를 통해 해당 기사와 매핑된 NEWS 타입 퀴즈 1문제를 조회합니다.")
    @GetMapping("/{news_id}/quiz")
    public ApiResponse<QuizResDTO> getNewsQuiz(
            @PathVariable("news_id") Long newsId
    ) {
        QuizResDTO responseData = newsService.getNewsQuizByNewsId(newsId);
        return ApiResponse.onSuccess(NewsSuccessCode.NEWS_QUIZ_GET_SUCCESS, responseData);
    }

    /**
     * [명세서 반영] 뉴스 독해 퀴즈 채점 및 최종 학습 완료 처리
     * POST /api/news/{news_id}/quiz/submit
     */
    @Operation(summary = "뉴스 독해 퀴즈 채점 및 학습완료 처리",
            description = "제출한 뉴스 퀴즈의 정답을 판별하고, 맞춘 경우 포인트 지급 및 해당 뉴스 학습완료 여부를 갱신합니다.")
    @PostMapping("/{news_id}/quiz/submit")
    public ApiResponse<QuizSubmitResDTO> submitNewsQuiz(
            @PathVariable("news_id") Long newsId,
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody QuizSubmitReqDTO request
    ) {
        // 필수 값 검증에 실패하면 서비스 계층 또는 Custom Exception에서 처리하도록 넘김
        QuizSubmitResDTO responseData = newsService.submitAndGradeNewsQuiz(authUser.getUserId(), newsId, request);
        return ApiResponse.onSuccess(NewsSuccessCode.NEWS_QUIZ_GRADE_SUCCESS, responseData);
    }

    /**
     * [배치 구동 API] 데일리 뉴스 강제 가동
     * POST /api/news/daily-workflow
     */
    @Operation(summary = "오늘의 데일리 뉴스 데이터 강제 생성 (배치 수집)",
            description = "네이버 오픈 API를 통해 당일 뉴스를 수집하고, Gemini 연동을 통해 키워드와 퀴즈 데이터를 한 번에 적재합니다.")
    @PostMapping("/daily-workflow")
    public ApiResponse<String> runDailyNewsWorkflow() {
        newsService.executeDailyNewsWorkflow();
        return ApiResponse.onSuccess(NewsSuccessCode.TODAY_NEWS_SUMMARY_GET_SUCCESS, "오늘의 뉴스 및 퀴즈 데이터 수집 파이프라인 가동 성공!");
    }

    @GetMapping("/send")
    @Operation(summary = "부모님께 학습 결과 전송", description = "오늘의 학습 데이터를 조회합니다.")
    public ApiResponse<NewsResDTO> getLearningResult(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                newsService.getLearningResult(authUser.getUserId()));
    }
}