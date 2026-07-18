package com.solux31.nubee_BE.domain.news.dto;

//뉴스 추출 1단계
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter @Setter
public class NewsAnalysisResult {
    private String summary;             // 3~5줄 요약
    private String mainKeyword;         // 메인 키워드 1개

    // 단순 String 리스트에서 객체 리스트로 변경하여 파싱 에러 방지
    private List<SubKeyword> subKeywords;

    private NewsQuiz newsQuiz;          // 뉴스 관련 퀴즈 내용

    // subKeywords 내부의 각 단어와 설명을 바인딩할 클래스 정의
    @Getter @Setter
    public static class SubKeyword {
        private String word;
        private String explanation;
    }

    @Getter @Setter
    public static class NewsQuiz {
        private String question;
        private List<String> options;   // 4지선다 보기 배열 (ex: ["보기1", "보기2", ...])
        private int answer;             // 정답 인덱스 (0~3)
        private String explanation;
    }
}
