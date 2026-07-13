package com.solux31.nubee_BE.domain.news.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TodayNewsResponse {
    private int total_count;
    private List<NewsDto> news_list;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NewsDto {
        private Long news_id;
        private String category;
        private String title;
        private String summary;
        private String image_url;
        private MainKeywordDto main_keyword;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MainKeywordDto {
        private Long keyword_id;
        private String word;
        private String explanation;
        private String example_sentence;
        private String keyword_type; // "MAIN"
    }
}
