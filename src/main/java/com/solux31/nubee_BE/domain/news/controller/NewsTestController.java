package com.solux31.nubee_BE.domain.news.controller;
// 뉴스 api 끌어오는거 swagger 확인해보기

import com.solux31.nubee_BE.domain.news.dto.NaverNewsResponse;
import com.solux31.nubee_BE.domain.news.service.NewsApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "뉴스 수집 테스트 API", description = "네이버 검색 API 연동 검증용 화면")
@RestController
@RequestMapping("/api/v1/test/news")
@RequiredArgsConstructor
public class NewsTestController {

    private final NewsApiService newsApiService;

    @Operation(summary = "네이버 뉴스 4개 카테고리 x 2개씩 총 8개 불러오기")
    @GetMapping("/naver-eight")
    public List<NaverNewsResponse.NaverNewsItem> testFetchAllCategories() {
        // 모든 카테고리 뉴스를 모아 담을 종합 바구니 생성
        List<NaverNewsResponse.NaverNewsItem> totalNewsList = new ArrayList<>();

        // 각 서비스 매핑 카테고리별로 2개씩 가져와서 담음 (기본 검색어인 100은 제외하고 실제 매핑 코드 사용)
        totalNewsList.addAll(newsApiService.fetchNewsByCategory("101", 2)); // 사회 2개
        totalNewsList.addAll(newsApiService.fetchNewsByCategory("102", 2)); // 과학 2개
        totalNewsList.addAll(newsApiService.fetchNewsByCategory("103", 2)); // 생활/문화 2개
        totalNewsList.addAll(newsApiService.fetchNewsByCategory("105", 2)); // 세계 2개 (실제 매핑 코드 적용)

        // 최종 리턴
        return totalNewsList;
    }
}