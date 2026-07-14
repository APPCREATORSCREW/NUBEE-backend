package com.solux31.nubee_BE.domain.words.controller;

import com.solux31.nubee_BE.domain.news.dto.QuizResponse;
import com.solux31.nubee_BE.domain.news.dto.QuizSubmitRequest;
import com.solux31.nubee_BE.domain.news.dto.QuizSubmitResponse;
import com.solux31.nubee_BE.domain.news.dto.TodayNewsResponse;
import com.solux31.nubee_BE.domain.news.service.NewsService;
import com.solux31.nubee_BE.domain.words.dto.KeywordDetailResponse;
import com.solux31.nubee_BE.domain.words.service.WordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Keyword API", description = "단어/키워드 상세 정보 및 퀴즈 관련 API")
@RestController
@RequestMapping("/api/v1/keywords")
@RequiredArgsConstructor
public class WordsController {

    private final WordService wordService;
    private final NewsService newsService;

    /**
     * [명세서 반영] 1번 특정 키워드 설명 조회
     */
    @Operation(summary = "선택한 키워드의 설명 조회",
            description = "키워드 ID를 통해 해당 단어의 이름, 초등 눈높이 설명, 예문, 타입을 반환합니다.")
    @GetMapping("/{keyword_id}")
    public ResponseEntity<?> getKeywordDetail(
            @PathVariable("keyword_id") Long keywordId
    ) {
        if (keywordId == null || keywordId <= 0) {
            return ResponseEntity.badRequest().body("유효하지 않은 키워드 ID 입력");
        }

        try {
            KeywordDetailResponse responseData = wordService.getKeywordDetail(keywordId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "선택한 메인 키워드의 상세 정보 조회가 완료되었습니다.");
            result.put("data", responseData);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * [명세서 반영] 2번 특정 키워드의 복습 퀴즈 조회
     * GET /api/v1/keywords/{keyword_id}/quiz
     */
    @Operation(summary = "특정 키워드의 퀴즈 조회",
            description = "키워드 ID를 통해 해당 단어와 연동된 KEYWORD 타입 퀴즈 1문제를 조회합니다.")
    @GetMapping("/{keyword_id}/quiz")
    public ResponseEntity<?> getKeywordQuiz(
            @PathVariable("keyword_id") Long keywordId
    ) {
        if (keywordId == null || keywordId <= 0) {
            return ResponseEntity.badRequest().body("키워드 ID 입력값 오류");
        }

        try {
            QuizResponse responseData = newsService.getKeywordQuizByKeywordId(keywordId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "해당 키워드의 퀴즈 조회가 완료되었습니다.");
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
     * [명세서 반영] 3번 키워드 퀴즈 채점 및 포인트 지급
     * POST /api/v1/keywords/{keyword_id}/quiz/submit
     */
    @Operation(summary = "키워드 퀴즈 채점 및 포인트 지급",
            description = "유저가 제출한 퀴즈 답안을 채점하여 정답 여부와 해설을 반환하고, 정답일 경우 1포인트를 지급합니다.")
    @PostMapping("/{keyword_id}/quiz/submit")
    public ResponseEntity<?> submitKeywordQuiz(
            @PathVariable("keyword_id") Long keywordId,
            @RequestBody QuizSubmitRequest request
    ) {
        if (keywordId == null || request.getQuiz_id() == null || request.getSelected_answer() <= 0) {
            return ResponseEntity.badRequest().body("필수 입력값 누락 (quiz_id, selected_answer)");
        }

        try {
            Long temporaryUserId = 1L; // 테스트용 임시 세팅

            // 나연님이 의도하신 구분용 서비스 메서드명 매핑 적용!
            QuizSubmitResponse responseData = newsService.submitAndGradeKeywordQuiz(temporaryUserId, keywordId, request);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "키워드 퀴즈 채점이 완료되었습니다.");
            result.put("data", responseData);

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("존재하지 않는")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * [명세서 반영 ] 오늘의 맞춤 키워드 및 뉴스 리스트 조회
     * GET /api/v1/keywords
     */
    @Operation(summary = "오늘의 맞춤 키워드 및 뉴스 리스트 조회",
            description = "유저 설정(preferred_keyword_count)에 맞춰 카테고리 균형을 잡은 단어 카드와 연관 뉴스 리스트를 반환합니다.")
    @GetMapping
    public ResponseEntity<?> getTodayKeywordsAndNews() {
        try {
            // [테스트용 임시 조치] 1번 유저 고정
            Long temporaryUserId = 1L;

            // 서비스 단에 유저 ID를 넘겨주어, 그 유저가 설정한 개수만큼 알아서 꺼내오도록 요청
            TodayNewsResponse responseData = newsService.getBalancedTodayNewsForUser(temporaryUserId);

            if (responseData.getNews_list().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("오늘 제공된 뉴스 및 키워드 학습 데이터가 존재하지 않습니다.");
            }

            // 명세서 규격 포장
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "오늘의 맞춤 키워드 및 뉴스 리스트 조회가 완료되었습니다.");
            result.put("data", responseData);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            // 유저의 설정 개수가 범위를 벗어나는 등 잘못된 데이터 예외 처리
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}