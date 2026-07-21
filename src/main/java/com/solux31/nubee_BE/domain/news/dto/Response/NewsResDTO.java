package com.solux31.nubee_BE.domain.news.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class NewsResDTO {

    private String username;
    private List<KeywordInfo> learnedKeywords;
    private double keywordQuizAccuracy;
    private double newsQuizAccuracy;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class KeywordInfo {
        private String word;
        private String originalUrl;
    }
}