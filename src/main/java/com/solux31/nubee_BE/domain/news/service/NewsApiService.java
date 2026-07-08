package com.solux31.nubee_BE.domain.news.service;

import com.solux31.nubee_BE.domain.news.dto.NaverNewsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
public class NewsApiService {

    @Value("${naver.api.client-id}")
    private String clientId;

    @Value("${naver.api.client-secret}")
    private String clientSecret;

    @Value("${naver.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    //특정 카테고리 키워드로 최신 뉴스 2개를 긁어오는 메서드
    public List<NaverNewsResponse.NaverNewsItem> fetchTwoNewsByCategory(String categoryKeyword) {
        // 1. 네이버 API는 헤더에 ID와 Secret을 요구
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Naver-Client-Id", clientId);
        headers.set("X-Naver-Client-Secret", clientSecret);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 2. URL 조립 (카테고리 키워드로 검색, 정확도순 정렬, 2개씩 가져오기)
        URI targetUri = UriComponentsBuilder.fromUriString(apiUrl)
                .queryParam("query", categoryKeyword)
                .queryParam("display", 2) // 각 카테고리에 대해 2개씩
                .queryParam("sort", "sim") // 관련도 높은 뉴스 검색
                .build()
                .encode()
                .toUri();

        try {
            ResponseEntity<NaverNewsResponse> response = restTemplate.exchange(
                    targetUri, HttpMethod.GET, entity, NaverNewsResponse.class);

            if (response.getBody() != null) {
                return response.getBody().getItems(); // 네이버가 준 2개짜리 리스트 리턴
            }
        } catch (Exception e) {
            System.out.println(categoryKeyword + " 뉴스 조회 중 에러 발생: " + e.getMessage());
        }
        return new ArrayList<>(); // 에러 나면 빈 바구니 리턴해서 튕김 방지
    }

    // 새벽 배치가 호출할 마스터 메서드, 설정된 4개 카테고리에서 각각 2개씩 총 8개의 뉴스를 수집
    public List<NaverNewsResponse.NaverNewsItem> fetchDailyEightNews() {
        // 4가지 카테고리 키워드를 리스트로 정의
        String[] categories = {"경제", "사회", "과학", "세계"};
        List<NaverNewsResponse.NaverNewsItem> totalEightNews = new ArrayList<>();

        for (String category : categories) {
            List<NaverNewsResponse.NaverNewsItem> newsList = fetchTwoNewsByCategory(category);
            if (newsList != null) {
                for (NaverNewsResponse.NaverNewsItem item : newsList) {
                    // 네이버에서 긁어온 직후, 카테고리 달아주기.
                    item.setCategory(category);
                    totalEightNews.add(item);
                }
            }
        }

        return totalEightNews; // 8개의 뉴스 리턴
    }
}