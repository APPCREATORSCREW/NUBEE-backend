package com.solux31.nubee_BE.domain.news.dto;

//뉴스 추출 1단계
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter @Setter
public class NewsAnalysisResult {
    private String summary;             // 3~5줄 요약
    private String mainKeyword;         // 메인 키워드 1개
    private List<String> subKeywords;   // 팝업용 서브 키워드 리스트
    private NewsQuiz newsQuiz;          // 뉴스 관련 퀴즈 내용

    @Getter @Setter
    public static class NewsQuiz {
        private String question;
        private List<String> options;   // 4지선다 보기 배열 (ex: ["보기1", "보기2", ...])
        private int answer;             // 정답 인덱스 (0~3)
        private String explanation;
    }
}
