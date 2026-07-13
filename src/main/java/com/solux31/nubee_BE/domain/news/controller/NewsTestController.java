package com.solux31.nubee_BE.domain.news.controller;
//뉴스 api 끌어오는거 swagger 확인해보기

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

@Tag(name = "뉴스 수집 테스트 API", description = "네이버 검색 API 연동 검증용 화면입니다.")
@RestController
@RequestMapping("/api/v1/test/news")
@RequiredArgsConstructor
public class NewsTestController {

    private final NewsApiService newsApiService;

    @Operation(summary = "네이버 뉴스 4개 카테고리 x 2개씩 총 8개 불러오기")
    @GetMapping("/naver-eight")
    public List<NaverNewsResponse.NaverNewsItem> testFetchAllCategories() {
        // 1. 모든 카테고리 뉴스를 모아 담을 종합 바구니 생성
        List<NaverNewsResponse.NaverNewsItem> totalNewsList = new ArrayList<>();

        // 2. 각 카테고리별로 1개씩 긁어와서 바구니에 담기 (.addAll 이용)
        totalNewsList.addAll(newsApiService.fetchNewsByCategory("100", 1)); // 경제 1개
        totalNewsList.addAll(newsApiService.fetchNewsByCategory("101", 1)); // 사회 1개
        totalNewsList.addAll(newsApiService.fetchNewsByCategory("102", 1)); // 과학 1개
        totalNewsList.addAll(newsApiService.fetchNewsByCategory("105", 1)); // 세계 1개 (ApiService 코드의 105번에 맞춤!)

        // 3. 다 모인 종합 바구니를 최종적으로 한 번만 리턴!
        return totalNewsList;
    }
}