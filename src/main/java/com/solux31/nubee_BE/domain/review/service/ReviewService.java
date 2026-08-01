package com.solux31.nubee_BE.domain.review.service;

import com.solux31.nubee_BE.domain.review.dto.Response.CategoryListResDTO;
import com.solux31.nubee_BE.domain.review.dto.Response.NewsItemResDTO;
import com.solux31.nubee_BE.domain.review.dto.Response.ReviewResDTO;
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

    public ReviewResDTO getReviewNews(Long userId, String category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserNewsHistory> historyPage =
                reviewRepository.findByUserIdAndCategory(userId, category, pageable);

        List<NewsItemResDTO> newsList = historyPage.getContent().stream()
                .map(history -> NewsItemResDTO.builder()
                        .newsId(history.getNews().getId())
                        .title(history.getNews().getTitle())
                        .imageUrl(history.getNews().getImageUrl())
                        .viewedAt(history.getViewedAt())
                        .build())
                .toList();

        return ReviewResDTO.builder()
                .category(category)
                .news(newsList)
                .build();
    }

    public CategoryListResDTO getCategories(Long userId) {
        List<String> categories = reviewRepository.findDistinctCategoriesByUserId(userId);
        return CategoryListResDTO.builder()
                .categories(categories)
                .build();
    }
}