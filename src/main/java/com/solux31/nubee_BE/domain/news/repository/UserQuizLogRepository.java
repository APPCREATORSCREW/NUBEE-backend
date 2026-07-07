package com.solux31.nubee_BE.domain.news.repository;

import com.solux31.nubee_BE.domain.news.entity.UserQuizLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserQuizLogRepository extends JpaRepository<UserQuizLog, Long> {
    // 이 유저가 이 퀴즈를 이미 풀었는지 확인하기 위한 메서드
    boolean existsByUserIdAndQuizId(Long userId, Long quizId);
}
