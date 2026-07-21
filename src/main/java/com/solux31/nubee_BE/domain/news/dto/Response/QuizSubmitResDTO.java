package com.solux31.nubee_BE.domain.news.dto.Response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuizSubmitResDTO {
    private Long quiz_id;
    private int selected_answer;
    private int correct_answer;

    @JsonProperty("is_correct")
    private boolean is_correct;

    private String explanation;

    @JsonProperty("is_completed")
    private boolean is_completed;

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