package com.solux31.nubee_BE.domain.news.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NewsDetailResDTO {
    private Long id;
    private String category;
    private String title;
    private String summary;
    private String image_url;
    private String original_url;
    private String publisher;
    private LocalDateTime published_at;
    private List<RelatedKeywordDto> related_keywords; // 하이라이팅용 단어 리스트

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedKeywordDto {
        private Long id;
        private String word;
        private String keyword_type;
        private String explanation;
    }
}
