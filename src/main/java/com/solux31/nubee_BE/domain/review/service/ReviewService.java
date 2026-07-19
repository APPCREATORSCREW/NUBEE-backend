package com.solux31.nubee_BE.domain.review.service;

import com.solux31.nubee_BE.domain.review.dto.ReviewResDTO;
import com.solux31.nubee_BE.domain.review.entity.UserNewsHistory;
import com.solux31.nubee_BE.domain.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviewRepository;

    public ReviewResDTO.ReviewResponse getReviewNews(Long userId, String category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserNewsHistory> historyPage =
                reviewRepository.findByUserIdAndCategory(userId, category, pageable);

        List<ReviewResDTO.NewsItem> newsList = historyPage.getContent().stream()
                .map(history -> ReviewResDTO.NewsItem.builder()
                        .newsId(history.getNews().getId())
                        .title(history.getNews().getTitle())
                        .imageUrl(history.getNews().getImageUrl())
                        .viewedAt(history.getViewedAt())
                        .build())
                .toList();

        return ReviewResDTO.ReviewResponse.builder()
                .category(category)
                .news(newsList)
                .build();
    }
}
