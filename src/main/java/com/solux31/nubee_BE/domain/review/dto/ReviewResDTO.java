package com.solux31.nubee_BE.domain.review.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;

public class ReviewResDTO {
    @Getter
    @Builder
    public static class ReviewResponse {
        private String category;
        private List<NewsItem> news;
    }

    @Getter
    @Builder
    public static class NewsItem {
        private Long newsId;
        private String title;
        private String imageUrl;
        private LocalDateTime viewedAt;
    }
}
