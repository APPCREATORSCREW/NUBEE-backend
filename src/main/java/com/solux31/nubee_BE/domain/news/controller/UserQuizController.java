package com.solux31.nubee_BE.domain.news.controller;

import com.solux31.nubee_BE.domain.news.dto.QuizSubmitRequest;
import com.solux31.nubee_BE.domain.news.service.UserQuizLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/quizzes")
@RequiredArgsConstructor
public class UserQuizController {

    private final UserQuizLogService userQuizLogService;

    /**
     * 유저 퀴즈 정답 제출 및 보상 지급 API
     * 테스트 편의를 위해 임시로 헤더(X-User-Id)를 통해 유저 식별자를 받습니다.
     */
    @PostMapping("/submit")
    public ResponseEntity<String> submitQuiz(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody QuizSubmitRequest request) {

        try {
            boolean isCorrect = userQuizLogService.submitQuizAnswer(userId, request);

            if (isCorrect) {
                return ResponseEntity.ok("정답입니다! 10포인트가 적립되었으며, 학습 이력이 성공적으로 기록되었습니다.");
            } else {
                return ResponseEntity.ok("오답입니다. 다음번에 더 잘할 수 있어요! 학습 이력이 기록되었습니다.");
            }
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}