package com.solux31.nubee_BE.domain.news.repository;

import com.solux31.nubee_BE.domain.news.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    // Quiz 안의 dailyNews 객체 내부 id를 참조하도록 변경 (dailyNews_Id 또는 dailyNewsId)
    Optional<Quiz> findByDailyNewsIdAndQuizType(Long newsId, String quizType);

    // Quiz 안의 keyword 객체 내부 id를 참조하도록 변경 (keyword_Id 또는 keywordId)
    Optional<Quiz> findByKeyword_IdAndQuizType(Long keywordId, String quizType);
}
