package com.solux31.nubee_BE.domain.news.service;

import com.solux31.nubee_BE.domain.news.dto.QuizSubmitRequest;
import com.solux31.nubee_BE.domain.news.entity.Quiz;
import com.solux31.nubee_BE.domain.news.entity.UserQuizLog;
import com.solux31.nubee_BE.domain.news.repository.QuizRepository;
import com.solux31.nubee_BE.domain.news.repository.UserQuizLogRepository;
import com.solux31.nubee_BE.domain.auth.entity.User; // 유저 엔티티 임포트
import com.solux31.nubee_BE.domain.auth.repository.UserRepository; // 유저 레포지토리 임포트
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 기본은 읽기 전용으로 설정
public class UserQuizLogService {

    private final QuizRepository quizRepository;
    private final UserQuizLogRepository userQuizLogRepository;
    private final UserRepository userRepository; // 포인트 지급을 위해 추가

    /**
     * 사용자가 제출한 퀴즈 답안을 채점하고 로그 저장 및 포인트 지급
     */
    @Transactional // 쓰기 작업이 포함되므로 @Transactional 적용
    public boolean submitQuizAnswer(Long userId, QuizSubmitRequest request) {

        // 1. 중복 체크
        if (userQuizLogRepository.existsByUserIdAndQuizId(userId, request.getQuiz_id())) {
            throw new IllegalStateException("이미 풀이한 퀴즈임.");
        }

        // 2. 퀴즈 및 유저 조회
        Quiz quiz = quizRepository.findById(request.getQuiz_id())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 퀴즈임."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저임."));

        // 3. 채점 진행
        boolean isCorrect = (quiz.getAnswer() == request.getSelected_answer());

        // 4. 포인트 지급 (정답 시 1포인트 지급 및 주석 일치화)
        if (isCorrect) {
            user.updatePoint(1);
        }

        boolean isAllCompleted = false;

        // 퀴즈와 연관된 dailyNews 안전하게 조회 (N+1 방지 혹은 지연로딩 고려)
        Long newsId = (quiz.getDailyNews() != null) ? quiz.getDailyNews().getId() : null;

        if (newsId != null) {
            if ("NEWS".equals(quiz.getQuizType())) {
                // 지금 푸는 게 뉴스 퀴즈라면, 같은 뉴스ID를 공유하는 단어 퀴즈(KEYWORD)를 풀었는지 DB에서 확인
                boolean isKeywordQuizSolved = userQuizLogRepository.existsByUserIdAndDailyNewsIdAndQuizIdNot(userId, newsId, quiz.getId());
                isAllCompleted = isKeywordQuizSolved; // 단어 퀴즈까지 이미 풀려있었다면 완료

            } else if ("KEYWORD".equals(quiz.getQuizType())) {
                // 지금 푸는 게 단어 퀴즈라면, 짝꿍인 뉴스 퀴즈(NEWS)가 이미 풀려있는지 확인
                boolean isNewsQuizSolved = userQuizLogRepository.existsByUserIdAndDailyNewsIdAndQuizIdNot(userId, newsId, quiz.getId());
                isAllCompleted = isNewsQuizSolved; // 뉴스 퀴즈까지 이미 풀려있었다면 완료
            }
        }

        // 5. UserQuizLog 엔티티 빌드 및 저장
        UserQuizLog quizLog = UserQuizLog.builder()
                .userId(userId)
                .quizId(quiz.getId())
                .selectedAnswer(request.getSelected_answer()) // 사용자가 고른 답
                .isCorrect(isCorrect)
                .isCompleted(isAllCompleted)
                .category(quiz.getCategory())
                .build();

        userQuizLogRepository.save(quizLog);

        return isCorrect;
    }
}