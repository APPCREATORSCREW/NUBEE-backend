package com.solux31.nubee_BE.domain.news.repository;

import com.solux31.nubee_BE.domain.news.entity.UserQuizLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserQuizLogRepository extends JpaRepository<UserQuizLog, Long> {

    // 메서드 1: 유저가 특정 퀴즈 ID를 풀었는지 검사 (유지)
    boolean existsByUserIdAndQuizId(Long userId, Long quizId);

    // 메서드 2: 카테고리 내 중복 검증용 (유지)
    boolean existsByUserIdAndCategoryAndQuizIdNot(Long userId, String category, Long quizId);

}