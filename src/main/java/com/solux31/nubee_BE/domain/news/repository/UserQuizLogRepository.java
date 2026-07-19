package com.solux31.nubee_BE.domain.news.repository;

import com.solux31.nubee_BE.domain.news.entity.UserQuizLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserQuizLogRepository extends JpaRepository<UserQuizLog, Long> {

    // 유저가 특정 퀴즈 ID를 풀었는지 검사 (유지)
    boolean existsByUserIdAndQuizId(Long userId, Long quizId);

    // 카테고리가 아닌 특정 뉴스 ID(dailyNews.id) 기준으로 동일 뉴스 내 타 퀴즈 중복 완료 상태 검증
    boolean existsByUserIdAndIdAndQuizIdNot(Long userId, Long id, Long quizId);

}