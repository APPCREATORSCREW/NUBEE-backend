package com.solux31.nubee_BE.domain.news.entity;

import com.solux31.nubee_BE.domain.words.entity.Keyword;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    private String category;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 1000)
    private String originalUrl;

    @Column(nullable = false)
    private LocalDateTime publishedAt; // 기사 발행일

    // 뉴스 하나에 여러 키워드가 묶이는 1:N 양방향 매핑
    @Builder.Default
    @OneToMany(mappedBy = "dailyNews", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Keyword> relatedKeywords = new ArrayList<>();

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}