package com.solux31.nubee_BE.domain.review.repository;

import com.solux31.nubee_BE.domain.review.entity.UserNewsHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<UserNewsHistory, Long> {
    @Query("SELECT h FROM UserNewsHistory h " +
            "JOIN FETCH h.news n " +      // ← 끝에 공백 추가
            "WHERE h.user.id = :userId " +
            "AND n.category = :category " +
            "ORDER BY h.viewedAt DESC")
    Page<UserNewsHistory> findByUserIdAndCategory(
            @Param("userId") Long userId,
            @Param("category") String category,
            Pageable pageable
    );

    @Query("SELECT DISTINCT n.category FROM UserNewsHistory h " +
            "JOIN h.news n " +
            "WHERE h.user.id = :userId")
    List<String> findDistinctCategoriesByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndNewsId(Long userId, Long newsId);
}
