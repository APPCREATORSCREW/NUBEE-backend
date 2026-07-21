package com.solux31.nubee_BE.domain.news.entity;

import com.solux31.nubee_BE.domain.words.entity.Keyword; // Keyword 임포트
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quiz_id") // DB 컬럼명은 일관성 있게 quiz_id로 매핑
    private Long id;

    // 1. DailyNews와의 N:1 단방향 연관 관계 (FK: news_id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_id", nullable = true)
    private DailyNews dailyNews;

    // 2. Keyword와의 N:1 단방향 연관 관계 (FK: keyword_id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id", nullable = true)
    private Keyword keyword;

    @Column(nullable = false, length = 20)
    private String quizType; // "KEYWORD" 또는 "NEWS"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question; // 퀴즈 질문

    @Column(nullable = false, columnDefinition = "TEXT")
    private String optionsJson; // 퀴즈 보기 JSON

    @Column(nullable = false)
    private int answer; // 정답 번호

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation; // 퀴즈 해설

    @Column(nullable = false, length = 20)
    private String category;

    public Long getNewsId() {
        return this.dailyNews != null ? this.dailyNews.getId() : null;
    }

    public Long getKeywordId() {
        return this.keyword != null ? this.keyword.getId() : null; // Keyword의 PK가 'id'로 바뀌었으므로 .getId() 호출
    }
}