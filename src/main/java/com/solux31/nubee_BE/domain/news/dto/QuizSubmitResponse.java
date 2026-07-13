package com.solux31.nubee_BE.domain.news.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuizSubmitResponse {
    private Long quiz_id;
    private int selected_answer;
    private boolean is_correct;
    private int correct_answer;
    private String explanation;
    private PointResultDto point_result;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PointResultDto {
        private int earned_point;
        private int current_point;
    }
}
