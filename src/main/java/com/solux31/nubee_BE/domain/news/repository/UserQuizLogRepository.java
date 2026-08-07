package com.solux31.nubee_BE.domain.news.repository;

import com.solux31.nubee_BE.domain.news.entity.mapping.UserQuizLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserQuizLogRepository extends JpaRepository<UserQuizLog, Long> {

    // 유저가 특정 퀴즈 ID를 풀었는지 검사 (유지)
    boolean existsByUserIdAndQuizId(Long userId, Long quizId);

    // 카테고리가 아닌 특정 뉴스 ID(dailyNews.id) 기준으로 동일 뉴스 내 타 퀴즈 중복 완료 상태 검증
    boolean existsByUserIdAndIdAndQuizIdNot(Long userId, Long id, Long quizId);

    /**
     * [추가] 유저가 가장 '적게 풀 수록' 학습이 부족하다고 판단 -> 풀이 로그 개수가 적은 순으로 카테고리 이름을 정렬해 반환
     *
     * @param userId 분석할 유저 ID
     * @param pageable 가져올 결과 개수를 제한하기 위한 페이징 객체 (ex: PageRequest.of(0, 1))
     * @return 적게 푼 순서대로 정렬된 카테고리 이름 리스트
     */
    @Query("SELECT uql.category FROM UserQuizLog uql " +
            "WHERE uql.userId = :userId " +
            "GROUP BY uql.category " +
            "ORDER BY COUNT(uql.id) ASC")
    List<String> findLeastSolvedCategories(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT DISTINCT uql.category FROM UserQuizLog uql WHERE uql.userId = :userId")
    List<String> findAllCategoriesByUserId(@Param("userId") Long userId);

    // 오늘 날짜 기준 특정 quiz_type 정답률 계산용 및 KEYWORD 타입 퀴즈 로그 조회
    @Query("SELECT uql FROM UserQuizLog uql " +
            "JOIN Quiz q ON uql.quizId = q.id " +
            "WHERE uql.userId = :userId " +
            "AND q.quizType = :quizType " +
            "AND DATE(uql.createdAt) = CURRENT_DATE")
    List<UserQuizLog> findTodayLogsByUserIdAndQuizType(
            @Param("userId") Long userId,
            @Param("quizType") String quizType);

    // 유저가 풀 완료한 퀴즈 ID 목록 전체 조회 (GET /api/keywords 회색 처리용)
    @Query("SELECT uql.quizId FROM UserQuizLog uql WHERE uql.userId = :userId AND uql.isCompleted = true")
    List<Long> findSolvedQuizIdsByUserId(@Param("userId") Long userId);
}