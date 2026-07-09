package com.solux31.nubee_BE.domain.news.service;

import com.solux31.nubee_BE.domain.news.dto.QuizSubmitRequest;
import com.solux31.nubee_BE.domain.news.entity.Quiz;
import com.solux31.nubee_BE.domain.news.entity.UserQuizLog;
import com.solux31.nubee_BE.domain.news.repository.QuizRepository;
import com.solux31.nubee_BE.domain.news.repository.UserQuizLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizRepository quizRepository;
    private final UserQuizLogRepository userQuizLogRepository;

    /**
     * 사용자가 제출한 퀴즈 답안을 채점하고 로그를 저장
     * @param userId 현재 로그인한 사용자 ID (Spring Security 연동 필요)
     */
    @Transactional
    public boolean submitQuizAnswer(Long userId, QuizSubmitRequest request) {
        // 1. 해당 퀴즈가 존재하는지 DB에서 조회
        Quiz quiz = quizRepository.findById(request.getQuizId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 퀴즈입니다."));

        // 2. 채점 진행 (사용자가 선택한 답과 실제 정답 인덱스 비교)
        boolean isCorrect = (quiz.getAnswer() == request.getSelectedAnswer());

        // 3. ERD 구조에 맞춰 UserQuizLog 엔티티 빌드 및 저장
        UserQuizLog quizLog = UserQuizLog.builder()
                 .userId(userId)           // ERD 연관관계에 따라 User 엔티티 객체 혹은 ID 매핑
                 //.quiz(quiz)               // Quiz 엔티티와 다대일(ManyToOne) 연관관계 매핑 권장
                .isCorrect(isCorrect)        // 정답 여부 (true/false)
                // .selectedOption(request.getSelectedAnswer()) // 사용자가 고른 답 저장 필드가 필요하다면 세팅
                .build();

        userQuizLogRepository.save(quizLog);

        // 4. 컨트롤러에 정답 여부 반환 (프론트에서 정답/오답 팝업 띄울 때 사용)
        return isCorrect;
    }
}
