package com.solux31.nubee_BE.domain.news.controller;

//swagger 테스트용 - gemini 테스트
import com.solux31.nubee_BE.domain.news.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    @Operation(summary = "🔥 데일리 뉴스 파이프라인 강제 실행",
            description = "전체 로직을 수동으로 즉시 실행하고, 실패 시 에러 원인을 화면에 표시합니다.")
    @GetMapping("/daily-workflow")
    public ResponseEntity<String> triggerDailyWorkflow() {
        try {
            System.out.println("🚀 [Swagger Test] 데일리 뉴스 파이프라인 가동 시작...");

            // 파이프라인 가동!
            newsService.executeDailyNewsWorkflow();

            System.out.println("✅ [Swagger Test] 파이프라인 정상 종료");
            return ResponseEntity.ok("🎉 데일리 뉴스 및 퀴즈 생성이 성공적으로 완료되었습니다! DB를 확인해 보세요.");

        } catch (Exception e) {
            e.printStackTrace();
            // 💡 에러가 나면 500 에러와 함께 원인 메시지를 Swagger 화면에 직접 뿌려줍니다!
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("❌ 파이프라인 실행 중 에러 발생: " + e.getMessage() + " (상세 로그는 인텔리제이 콘솔을 확인하세요.)");
        }
    }
}