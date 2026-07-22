package com.solux31.nubee_BE.domain.review.dto.Response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NewsItemResDTO {
    private Long newsId;
    private String title;
    private String imageUrl;
    private LocalDateTime viewedAt;
}
