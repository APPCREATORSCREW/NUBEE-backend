package com.solux31.nubee_BE.domain.news.repository;

import com.solux31.nubee_BE.domain.news.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    // 특정 뉴스 기사에 묶인 퀴즈 중, 내가 원하는 타입(KEYWORD 또는 NEWS)의 단 한 건만 명확히 조회
    Optional<Quiz> findByNewsIdAndQuizType(Long newsId, String quizType);
}
