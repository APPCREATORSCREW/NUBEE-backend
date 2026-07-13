package com.solux31.nubee_BE.domain.news.controller;

import com.solux31.nubee_BE.domain.news.dto.KeywordQuizResponse;
import com.solux31.nubee_BE.domain.news.dto.QuizSubmitRequest;
import com.solux31.nubee_BE.domain.news.dto.QuizSubmitResponse;
import com.solux31.nubee_BE.domain.news.dto.TodayNewsResponse;
import com.solux31.nubee_BE.domain.news.repository.DailyNewsRepository;
import com.solux31.nubee_BE.domain.news.repository.QuizRepository;
import com.solux31.nubee_BE.domain.news.service.NewsService;
import com.solux31.nubee_BE.domain.words.repository.KeywordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "News API", description = "데일리 뉴스 조회 및 생성 파이프라인 관련 API")
@RestController
@RequestMapping("/api/v1/news") // 정식 서비스 API 주소 규격으로 통일
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;
    private final DailyNewsRepository dailyNewsRepository;
    private final QuizRepository quizRepository;
    private final KeywordRepository keywordRepository;

    /**
     * [1번 기능: 정식 서비스용] 오늘의 맞춤 키워드 및 뉴스 리스트 조회
     * GET /api/v1/news?count=6
     */
    @Operation(summary = "오늘의 맞춤 키워드 및 뉴스 리스트 조회",
            description = "회원 정보(preferred_keyword_count)에 설정된 개수에 맞춰 카테고리 균형을 잡은 뉴스 리스트를 반환합니다.")
    @GetMapping
    public ResponseEntity<?> getTodayKeywordsAndNews(
            @RequestHeader("Authorization") String token
    ) {
        try {
            // 1. JWT 토큰에서 유저 ID 혹은 이메일을 추출하는 로직 (기존 프로젝트 보안 헬퍼 활용)
            // Long userId = jwtTokenProvider.getUserId(token);

            Long temporaryUserId = 1L; // 💡 테스트용 임시 유저 ID (실제 유저 식별 코드로 교체 필요)

            // 2. 서비스 단에 유저 ID를 넘겨주어, 그 유저가 설정한 개수만큼 알아서 꺼내오도록 요청!
            TodayNewsResponse responseData = newsService.getBalancedTodayNewsForUser(temporaryUserId);

            if (responseData.getNews_list().isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("오늘 제공된 뉴스 학습 데이터가 존재하지 않습니다.");
            }

            // 명세서 규격 포장
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "오늘의 맞춤 키워드 및 뉴스 리스트 조회가 완료되었습니다.");
            result.put("data", responseData);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            // 유저의 설정 개수가 3~6 범위를 벗어나는 등 잘못된 데이터 예외 처리
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * [2번 기능: 개발자 테스트용] 데일리 뉴스 파이프라인 강제 실행 & 결과 확인
     * GET /api/v1/news/daily-workflow
     */
    @Operation(summary = "[테스트용] 데일리 뉴스 파이프라인 강제 실행",
            description = "네이버 API 수집 및 Gemini 연성 로직을 즉시 실행하여 DB에 새 데이터를 채워 넣습니다.")
    @GetMapping("/daily-workflow") // 주소가 /api/v1/news/daily-workflow 가 됩니다.
    public ResponseEntity<?> triggerDailyWorkflow() {
        try {
            System.out.println("[Swagger Test] 데일리 뉴스 파이프라인 가동 시작...");

            // 파이프라인 가동! (DB 싹 비우고 네이버 긁어와서 Gemini 연성하기)
            newsService.executeDailyNewsWorkflow();

            System.out.println("[Swagger Test] 파이프라인 정상 종료 ➡ DB 데이터 추출 시작");

            var latestNews = dailyNewsRepository.findAll();
            var latestQuizzes = quizRepository.findAll();
            var latestKeywords = keywordRepository.findAll(Sort.by(Sort.Direction.DESC, "keywordId"));

            WorkflowResult result = new WorkflowResult(
                    "데일리 뉴스 및 퀴즈 생성을 성공했습니다!",
                    latestNews,
                    latestQuizzes,
                    latestKeywords
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("파이프라인 실행 중 에러 발생: " + e.getMessage());
        }
    }

    /**
     * [명세서 반영] 3번 특정 키워드 퀴즈 조회
     * GET /api/v1/keywords/{keyword_id}/quiz
     */
    @Operation(summary = "특정 키워드의 복습 퀴즈 조회",
            description = "키워드 ID를 통해 해당 단어와 연동된 퀴즈 1문제를 조회합니다. (정답과 해설은 보안상 제외)")
    @GetMapping("/api/v1/keywords/{keyword_id}/quiz") // 💡 명세서에 명시된 URL 절대 경로 매핑
    public ResponseEntity<?> getKeywordQuiz(
            @RequestHeader("Authorization") String token,
            @PathVariable("keyword_id") Long keywordId
    ) {
        // 400 에러 방어
        if (keywordId == null || keywordId <= 0) {
            return ResponseEntity.badRequest().body("키워드 ID 입력값 오류");
        }

        try {
            // 퀴즈 서비스 레이어 호출 (여기서는 편의상 newsService에 구현한다고 가정합니다)
            KeywordQuizResponse responseData = newsService.getKeywordQuizByKeywordId(keywordId);

            // 명세서 양식대로 리턴 포장
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "해당 키워드의 퀴즈 조회가 완료되었습니다.");
            result.put("data", responseData);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            // 404 에러 처리: 연동된 퀴즈가 없을 때
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * [명세서 반영] 4번 키워드 퀴즈 채점 및 포인트 지급
     * POST /api/v1/keywords/{keyword_id}/quiz/submit
     */
    @Operation(summary = "키워드 퀴즈 채점 및 포인트 지급",
            description = "유저가 제출한 답안을 채점하여 정답 여부와 해설을 반환하고, 맞춘 경우 1포인트를 지급합니다.")
    @PostMapping("/api/v1/keywords/{keyword_id}/quiz/submit")
    public ResponseEntity<?> submitKeywordQuiz(
            @RequestHeader("Authorization") String token,
            @PathVariable("keyword_id") Long keywordId,
            @RequestBody QuizSubmitRequest request // 👈 JSON 바디 접수!
    ) {
        // 400 에러 방어: 필수 입력값 누락 검증
        if (keywordId == null || request.getQuiz_id() == null || request.getSelected_answer() <= 0) {
            return ResponseEntity.badRequest().body("필수 입력값 누락 (quiz_id, selected_answer)");
        }

        try {
            // [테스트용 임시 조치] 이전과 동일하게 1번 유저로 가짜 유저 고정
            Long temporaryUserId = 1L;

            // 서비스 레이어 호출하여 채점 및 포인트 합산 진행
            QuizSubmitResponse responseData = newsService.submitAndGradeQuiz(temporaryUserId, keywordId, request);

            // 명세서 양식 규격 포장
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "퀴즈 채점이 완료되었습니다.");
            result.put("data", responseData);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            // 404: 존재하지 않는 퀴즈 ID / 400: 이미 완료된 키워드 퀴즈에 대한 제출 요청
            if (e.getMessage().contains("존재하지 않는")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Swagger 출력용 내부 DTO
    @Getter
    @AllArgsConstructor
    static class WorkflowResult {
        private String message;
        private Object savedNewsList;
        private Object savedQuizList;
        private Object savedKeywordList;
    }
}