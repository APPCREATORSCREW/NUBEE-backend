package com.solux31.nubee_BE.domain.news.repository;

import com.solux31.nubee_BE.domain.news.entity.UserQuizLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserQuizLogRepository extends JpaRepository<UserQuizLog, Long> {
    // 이 유저가 이 퀴즈를 이미 풀었는지 확인하기 위한 메서드
    boolean existsByUserIdAndQuizId(Long userId, Long quizId);

    // 특정 유저가 같은 카테고리 내에서 '현재 내 퀴즈 ID가 아닌 다른 퀴즈'를 푼 적이 있는지 체크
    // 오늘의 퀴즈가 카테고리당 뉴스1 + 단어1 한 세트로 깔끔하게 매칭되어 나오기 때문에 이 쿼리로 검증 가능
    boolean existsByUserIdAndCategoryAndQuizIdNot(Long userId, String category, Long quizId);
}
