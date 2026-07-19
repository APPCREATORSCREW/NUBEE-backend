package com.solux31.nubee_BE.domain.news.repository;

import com.solux31.nubee_BE.domain.news.entity.DailyNews;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DailyNewsRepository extends JpaRepository<DailyNews, Long> {
    @Query("SELECT d FROM DailyNews d JOIN d.relatedKeywords k WHERE k.word = :keywordName ORDER BY d.id DESC LIMIT 1")
    Optional<DailyNews> findTopByKeywordNameOrderByIdDesc(@Param("keywordName") String keywordName);

    List<DailyNews> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    boolean existsByOriginalUrl(String originalUrl);

    Optional<DailyNews> findByOriginalUrl(String originalUrl);
}
