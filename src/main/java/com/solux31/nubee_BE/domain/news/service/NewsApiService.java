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
    public List<NaverNewsResponse.NaverNewsItem> fetchNewsByCategory(String categoryCode, int displayCount) {
        // 1. 네이버 API는 헤더에 ID와 Secret을 요구
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Naver-Client-Id", clientId);
        headers.set("X-Naver-Client-Secret", clientSecret);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 2. 카테고리 코드를 네이버 API 검색어 키워드로 변환 ("101" -> "경제")
        String queryKeyword = convertCodeToKeyword(categoryCode);

        // 3. URL 조립 (정확도순 정렬, 지정된 개수만큼 가져오기)
        URI targetUri = UriComponentsBuilder.fromUriString(apiUrl)
                .build()
                .expand()
                .toUri();

        targetUri = UriComponentsBuilder.fromUri(targetUri)
                .queryParam("query", queryKeyword)
                .queryParam("display", displayCount)
                .queryParam("sort", "sim")
                .build()
                .encode()
                .toUri();

        try {
            ResponseEntity<NaverNewsResponse> response = restTemplate.exchange(
                    targetUri, HttpMethod.GET, entity, NaverNewsResponse.class);

            if (response.getBody() != null) {
                return response.getBody().getItems();
            }
        } catch (Exception e) {
            System.out.println(queryKeyword + " 뉴스 조회 중 에러 발생: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * 내부 헬퍼 메서드: NewsService에서 넘겨준 카테고리 코드를
     * 네이버 뉴스 검색어 API에 던질 알맞은 키워드로 치환
     */
    private String convertCodeToKeyword(String categoryCode) {
        return switch (categoryCode) {
            case "100" -> "경제";
            case "101" -> "사회";
            case "102" -> "과학";
            case "105" -> "세계";
            default -> "일반상식";
        };
    }
}