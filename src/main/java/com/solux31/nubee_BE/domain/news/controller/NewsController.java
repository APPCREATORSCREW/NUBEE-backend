package com.solux31.nubee_BE.domain.news.controller;

//swagger 테스트용 - gemini 테스트
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Premium News Test API", description = "데일리 뉴스 생성 파이프라인 테스트용 API")
@RestController
@RequestMapping("/api/test/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;
    private final DailyNewsRepository dailyNewsRepository;
    private final QuizRepository quizRepository;
    private final KeywordRepository keywordRepository;

    @Operation(summary = "데일리 뉴스 파이프라인 강제 실행 & 결과 확인",
            description = "전체 로직을 즉시 실행한 후, 방금 DB에 적재된 최신 뉴스 리스트와 퀴즈 목록을 Swagger 화면에 한눈에 뿌려줍니다.")
    @GetMapping("/daily-workflow")
    public ResponseEntity<?> triggerDailyWorkflow() {
        try {
            System.out.println("[Swagger Test] 데일리 뉴스 파이프라인 가동 시작...");

            // 1. 파이프라인 가동! (기존 로직 수행)
            newsService.executeDailyNewsWorkflow();

            System.out.println("[Swagger Test] 파이프라인 정상 종료 ➡ DB 데이터 추출 시작");

            // 2. 레포지토리에서 방금 들어간 데이터들 긁어오기
            var latestNews = dailyNewsRepository.findAll();
            var latestQuizzes = quizRepository.findAll();
            var latestKeywords = keywordRepository.findAll(Sort.by(Sort.Direction.DESC, "keywordId"));

            // 3. 묶어서 Swagger 화면에 리턴해주기
            WorkflowResult result = new WorkflowResult(
                    "🎉 데일리 뉴스 및 퀴즈 생성을 성공했습니다!",
                    latestNews,
                    latestQuizzes,
                    latestKeywords
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ 파이프라인 실행 중 에러 발생: " + e.getMessage() + " (상세 로그는 인텔리제이 콘솔을 확인하세요.)");
        }
    }

    // 💡 Swagger 출력용 예쁜 바구니 객체 (DTO)
    @Getter
    @AllArgsConstructor
    static class WorkflowResult {
        private String message;
        private Object savedNewsList;
        private Object savedQuizList;
        private Object savedKeywordList;
    }
}