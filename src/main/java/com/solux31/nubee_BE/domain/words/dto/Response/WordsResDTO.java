package com.solux31.nubee_BE.domain.words.dto.Response;

import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WordsResDTO {

    private List<WordItem> todayWords;
    private List<WordItem> previousWords;

    @Getter
    @AllArgsConstructor
    public static class WordItem {
        private Long userKeywordId;
        private Long keywordId;
        private String word;
        private String explanation;
        private String exampleSentence;
    }

}