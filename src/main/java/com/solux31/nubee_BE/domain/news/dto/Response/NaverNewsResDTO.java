package com.solux31.nubee_BE.domain.news.dto.Response;

import lombok.Getter;
import java.util.List;

@Getter
public class NaverNewsResDTO {

    private List<NaverNewsItem> items; // 검색 결과 기사들이 담기는 리스트

    @Getter
    public static class NaverNewsItem {
        private String title;       // 뉴스 기사 제목

        // DailyNews에 추가한 originalUrl로 들어갈 네이버 뉴스 링크
        // 원본 링크는 originallink, 크롤링 위해 네이버 뉴스로 연결
        private String link;

        private String description; // 뉴스 기사 본문 요약 (Gemini에게 던져줄 원문 원재료)
        private String pubDate;     // 기사 작성일

        // 네이버에서 제공하지 않기에 어떤 카테고리인지 기억해두기 위한 장치
        private String category;

        // 카테고리를 강제로 세팅해주기 위한 Setter 메서드
        public void setCategory(String category) {
            this.category = category;
        }
    }
}
