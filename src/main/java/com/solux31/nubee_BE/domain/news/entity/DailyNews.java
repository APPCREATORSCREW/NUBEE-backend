package com.solux31.nubee_BE.domain.news.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DailyNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_id") // DB 컬럼명은 news_id로 유지하되 자바 필드명은 id로
    private Long id; // newsId -> id로 변경

    @Column(nullable = true)
    private Long keywordId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private String mainKeyword;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    private String category;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 1000)
    private String originalUrl;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}