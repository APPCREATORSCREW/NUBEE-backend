package com.solux31.nubee_BE.domain.news.dto;

import lombok.Getter;
import java.util.List;

@Getter
public class NaverNewsResponse {
    private List<NaverNewsItem> items; // 검색 결과 기사들이 담기는 리스트

    @Getter
    public static class NaverNewsItem {
        private String title;       // 뉴스 기사 제목
        private String originallink;// DailyNews에 추가한 originalUrl로 들어갈 원본 링크
        private String description; // 뉴스 기사 본문 요약 (Gemini에게 던져줄 원문 원재료)
        private String pubDate;     // 기사 작성일
    }
}
