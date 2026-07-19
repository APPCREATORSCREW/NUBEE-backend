package com.solux31.nubee_BE.domain.news.repository;

import com.solux31.nubee_BE.domain.news.entity.DailyNews;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DailyNewsRepository extends JpaRepository<DailyNews, Long> {
    Optional<DailyNews> findTopByMainKeywordOrderByIdDesc(String mainKeyword);

    List<DailyNews> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    boolean existsByOriginalUrl(String originalUrl);
}
