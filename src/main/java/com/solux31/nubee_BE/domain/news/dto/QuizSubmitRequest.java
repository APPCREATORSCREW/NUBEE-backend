package com.solux31.nubee_BE.domain.news.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class QuizSubmitRequest {
    private Long quizId;      // 풀이한 퀴즈 ID
    private int selectedAnswer; // 사용자가 고른 보기 인덱스 (0~3)
}
