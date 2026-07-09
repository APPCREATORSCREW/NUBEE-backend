package com.solux31.nubee_BE.domain.news.dto;
//main 키워드 묶음용

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter @Setter
public class MainKeywordResult {
    private String keyword;             // 메인 키워드 명
    private String explanation;         // 초등학생용 설명
    private KeywordQuiz keywordQuiz;    // 키워드 관련 퀴즈 내용

    @Getter @Setter
    public static class KeywordQuiz {
        private String question;
        private List<String> options;   // 4지선다 보기 배열
        private int answer;             // 정답 인덱스 (0~3)
        private String explanation;
    }
}
