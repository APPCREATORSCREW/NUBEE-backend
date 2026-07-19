package com.solux31.nubee_BE.domain.words.entity;

import com.solux31.nubee_BE.domain.news.entity.DailyNews;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder

public class Keyword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "keyword_id")
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String word; // 예: "우주선", "금리"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation; // 단어 뜻풀이 설명

    @Column(columnDefinition = "TEXT")
    private String exampleSentence;

    @Column(nullable = false, length = 20)
    private String keywordType; // "MAIN" 또는 "SUB" 구분

    // 이 단어가 속한 뉴스 ID (DailyNews와 연결고리)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_id", nullable = false)
    private DailyNews dailyNews;

    // 연관관계 편의 메서드
    public void setDailyNews(DailyNews dailyNews) {
        this.dailyNews = dailyNews;
        if (!dailyNews.getRelatedKeywords().contains(this)) {
            dailyNews.getRelatedKeywords().add(this);
        }
    }

    public void updateExplanation(String explanation) {
        this.explanation = explanation;
    }

    public void updateExampleSentence(String exampleSentence) {
        this.exampleSentence = exampleSentence;
    }
}