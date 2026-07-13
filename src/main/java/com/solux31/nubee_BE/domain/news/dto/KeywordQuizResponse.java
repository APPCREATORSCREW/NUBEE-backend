package com.solux31.nubee_BE.domain.news.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

//특정키워드퀴즈조회에 사용하는 dto
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KeywordQuizResponse {
    private Long quiz_id;
    private Long news_id;
    private Long keyword_id;
    private String quiz_type;
    private String question;
    private List<OptionDto> options; // 파싱된 보기 배열

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionDto {
        private int option_number;
        private String option_text;
    }
}
