package com.solux31.nubee_BE.domain.news.controller;

//swagger 테스트용 - gemini 테스트
import com.solux31.nubee_BE.domain.news.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    @Operation(summary = "데일리 뉴스 파이프라인 강제 실행",
            description = "새벽 배치가 돌리는 전체 로직(네이버 수집 + 크롤링 + Gemini 연성 + DB 저장)을 수동으로 즉시 실행합니다.")
    @GetMapping("/daily-workflow")
    public ResponseEntity<String> triggerDailyWorkflow() {

        System.out.println("🚀 [Swagger Test] 데일리 뉴스 파이프라인 가동 시작...");

        // 우리가 열심히 만든 대망의 파이프라인 메서드 호출!
        newsService.executeDailyNewsWorkflow();

        System.out.println("✅ [Swagger Test] 파이프라인 정상 종료 및 DB 적재 완료!");

        return ResponseEntity.ok("데일리 뉴스 및 퀴즈 생성이 성공적으로 완료되었습니다! DB를 확인해 보세요.");
    }
}
