package com.solux31.nubee_BE.domain.review.dto.Response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ReviewResDTO {
    private String category;
    private List<NewsItemResDTO> news;
}
