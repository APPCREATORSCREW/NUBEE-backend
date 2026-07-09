package com.solux31.nubee_BE.domain.news.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 안전한 엔티티 생성을 위한 스프링 필수 어노테이션
@AllArgsConstructor
@Builder

public class DailyNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB가 ID를 1, 2, 3... 자동으로 올려줌
    private Long newsId;

    @Column(nullable = false)
    private Long keywordId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private String mainKeyword;

    @Column(nullable = false, columnDefinition = "TEXT") // 본문은 기니까 TEXT 타입으로
    private String summary; // Gemini가 요약해 준 어린이용 요약문

    @Column(nullable = false)
    private String category; // SCIENCE, ECONOMY 등등

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false, length = 1000) // URL은 길어질 수 있으므로 길이를 넉넉하게!
    private String originalUrl; //원본 뉴스 기사 링크 URL

    private LocalDateTime createdAt;

    // 비즈니스 로직에 필요할 때 등록될 날짜를 자동으로 넣어주는 설정
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}