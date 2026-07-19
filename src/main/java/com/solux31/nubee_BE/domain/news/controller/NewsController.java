package com.solux31.nubee_BE.domain.news.controller;

import com.solux31.nubee_BE.domain.news.dto.*;
import com.solux31.nubee_BE.domain.news.service.NewsService;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    /**
     * 오늘의 맞춤 키워드 및 뉴스 리스트 조회
     * GET /api/v1/keywords
     */
    @Operation(summary = "오늘의 맞춤 키워드 및 뉴스 리스트 조회",
            description = "사용자의 취약 카테고리를 분석하여 커스텀된 오늘 자 뉴스 배열과 메인 키워드를 반환합니다.")
    @GetMapping("/keywords")
    public ResponseEntity<?> getTodayNews(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        try {
            TodayNewsResponse responseData = newsService.getBalancedTodayNewsForUser(authUser.getUserId());
            return ResponseEntity.ok(responseData);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * [명세서 반영] 2번 오늘의 뉴스 상세 정보 조회
     * GET /api/v1/news/{news_id}
     */
    @Operation(summary = "특정 뉴스 상세 조회",
            description = "뉴스 기사 본문 요약, 원문 출처 링크, 그리고 해당 뉴스에 태깅된 주요 어휘 키워드 리스트를 일괄 반환합니다.")
    @GetMapping("/news/{news_id}")
    public ResponseEntity<?> getNewsDetail(
            @PathVariable("news_id") Long newsId
    ) {
        try {
            NewsDetailResponse responseData = newsService.getNewsDetailWithKeywords(newsId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "뉴스 상세 정보 조회가 완료되었습니다.");
            result.put("data", responseData);

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * [명세서 반영] 5번 특정 뉴스 독해 퀴즈 조회
     * GET /api/v1/news/{news_id}/quiz
     */
    @Operation(summary = "특정 뉴스의 퀴즈 조회",
            description = "뉴스 ID를 통해 해당 기사와 매핑된 NEWS 타입 퀴즈 1문제를 조회합니다.")
    @GetMapping("/news/{news_id}/quiz") 
    public ResponseEntity<?> getNewsQuiz(
            @PathVariable("news_id") Long newsId
    ) {
        try {
            QuizResponse responseData = newsService.getNewsQuizByNewsId(newsId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "해당 뉴스의 독해 퀴즈 조회가 완료되었습니다.");
            result.put("data", responseData);

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * [명세서 반영] 6번 뉴스 독해 퀴즈 채점 및 최종 학습 완료 처리
     * POST /api/v1/news/{news_id}/quiz/submit
     */
    @Operation(summary = "뉴스 독해 퀴즈 채점 및 학습완료 처리",
            description = "제출한 뉴스 퀴즈의 정답을 판별하고, 맞춘 경우 포인트 지급 및 해당 뉴스 학습완료 여부를 갱신합니다.")
    @PostMapping("/news/{news_id}/quiz/submit") // 💡 세부 경로에 /news 추가
    public ResponseEntity<?> submitNewsQuiz(
            @PathVariable("news_id") Long newsId,
            @AuthenticationPrincipal AuthUser authUser, // 💡 하드코딩 1L 걷어내고 실제 유저 주입!
            @RequestBody QuizSubmitRequest request
    ) {
        if (request.getQuiz_id() == null || request.getSelected_answer() <= 0) {
            return ResponseEntity.badRequest().body("필수 입력값 누락 (quiz_id, selected_answer)");
        }

        try {
            // 하드코딩 대신 authUser.getUserId() 반영
            QuizSubmitResponse responseData = newsService.submitAndGradeNewsQuiz(authUser.getUserId(), newsId, request);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "뉴스 퀴즈 채점이 완료되었습니다.");
            result.put("data", responseData);

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("존재하지 않는") || e.getMessage().contains("속하지 않은")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * [배치 구동 API] 데일리 뉴스 강제 가동
     * POST /api/v1/news/daily-workflow
     */
    @Operation(summary = "오늘의 데일리 뉴스 데이터 강제 생성 (배치 수집)",
            description = "네이버 오픈 API를 통해 당일 뉴스를 수집하고, Gemini 연동을 통해 키워드와 퀴즈 데이터를 한 번에 적재합니다.")
    @PostMapping("/news/daily-workflow") // 💡 세부 경로에 /news 추가
    public ResponseEntity<?> runDailyNewsWorkflow() {
        try {
            newsService.executeDailyNewsWorkflow();
            return ResponseEntity.ok("오늘의 뉴스 및 퀴즈 데이터 수집 파이프라인 가동 성공!");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("파이프라인 구동 중 실패: " + e.getMessage());
        }
    }
}