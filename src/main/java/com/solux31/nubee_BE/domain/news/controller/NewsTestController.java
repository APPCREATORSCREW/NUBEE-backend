package com.solux31.nubee_BE.domain.news.controller;

import com.solux31.nubee_BE.domain.news.dto.NaverNewsResponse;
import com.solux31.nubee_BE.domain.news.service.NewsApiService;
import com.solux31.nubee_BE.domain.news.service.NewsService; // 추가된 서비스
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "[개발용] 뉴스 로직 검증 API", description = "백엔드 로직 및 API 연동을 확인하기 위한 테스트용 컨트롤러")
@RestController
@RequestMapping("/api/v1/dev-test/news") // 경로도 dev-test로 명확히 분리
@RequiredArgsConstructor
public class NewsTestController {

    private final NewsApiService newsApiService;
    private final NewsService newsService; // 퀴즈 기반 맞춤 배분 로직을 검증하기 위해 주입

    @Operation(summary = "네이버 뉴스 4개 카테고리 x 2개씩 총 8개 불러오기 (수집 테스트)")
    @GetMapping("/naver-eight")
    public List<NaverNewsResponse.NaverNewsItem> testFetchAllCategories() {
        List<NaverNewsResponse.NaverNewsItem> totalNewsList = new ArrayList<>();

        totalNewsList.addAll(newsApiService.fetchNewsByCategory("101", 2)); // 사회
        totalNewsList.addAll(newsApiService.fetchNewsByCategory("102", 2)); // 과학
        totalNewsList.addAll(newsApiService.fetchNewsByCategory("103", 2)); // 생활/문화
        totalNewsList.addAll(newsApiService.fetchNewsByCategory("105", 2)); // 세계

        return totalNewsList;
    }

    @Operation(summary = "유저별 취약 카테고리 우선 배분 로직 검증", description = "유저 ID를 넣었을 때, 해당 유저가 가장 안 푼 카테고리의 뉴스가 리스트 최상단에 잘 꽂히는지 확인합니다.")
    @GetMapping("/balanced-today/{userId}")
    public ResponseEntity<?> testGetBalancedTodayNews(
            @Parameter(description = "테스트할 유저의 고유 ID", example = "1")
            @PathVariable Long userId) {

        var result = newsService.getBalancedTodayNewsForUser(userId);

        return ResponseEntity.ok(result);
    }
}