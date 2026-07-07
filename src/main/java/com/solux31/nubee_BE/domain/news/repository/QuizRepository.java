package com.solux31.nubee_BE.domain.news.repository;

import com.solux31.nubee_BE.domain.news.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    // 특정 뉴스 기사당 "하나의 퀴즈"만 쏙 찾아오는 메서드
    Optional<Quiz> findByNewsId(Long newsId);
}
