package com.solux31.nubee_BE.domain.words.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WordsResDTO {

    private Long userKeywordId;
    private Long keywordId;
    private String word;
    private String explanation;
    private String exampleSentence;
    private boolean isLearned;
}