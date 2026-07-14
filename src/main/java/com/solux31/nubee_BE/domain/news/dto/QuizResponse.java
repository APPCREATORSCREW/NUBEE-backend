package com.solux31.nubee_BE.domain.news.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

//특정퀴즈조회에 사용하는 dto
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuizResponse {
    private Long quiz_id;
    private Long news_id;
    private Long keyword_id; // 뉴스 퀴즈일 때는 null, 키워드 퀴즈일 때는 id가 담김
    private String quiz_type; // "KEYWORD" 또는 "NEWS"
    private String question;
    private List<OptionDto> options;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionDto {
        private int option_number;
        private String option_text;
    }
}
