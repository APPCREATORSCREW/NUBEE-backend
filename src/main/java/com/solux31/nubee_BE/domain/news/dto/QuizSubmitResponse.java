package com.solux31.nubee_BE.domain.news.dto;

import com.fasterxml.jackson.annotation.JsonInclude; // 💡 중요: null인 필드는 JSON 결과에서 자동으로 쏙 빼주는 마법의 어노테이션!
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // 값이 null인 필드는 JSON 변환 시 화면에 출력하지 않음
public class QuizSubmitResponse {
    private Long quiz_id;
    private int selected_answer;
    private boolean is_correct;
    private int correct_answer;
    private String explanation;

    // 1. 키워드 퀴즈 채점용 피드백 (뉴스 퀴즈 때는 null로 대입해서 안 보이게 만듦)
    private PointResultDto point_result;

    // 2. 뉴스 퀴즈 채점용 피드백 (키워드 퀴즈 때는 null로 대입해서 안 보이게 만듦)
    private LearningResultDto learning_result;

    // --- Inner DTO 1: 포인트 결과용 ---
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointResultDto {
        private int earned_point;
        private int current_point;
    }

    // --- Inner DTO 2: 학습 완료 결과용 ---
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningResultDto {
        private int earned_point;
        private int current_point;
        private boolean is_completed;
    }
}
