package com.solux31.nubee_BE.domain.review.entity;

import com.solux31.nubee_BE.domain.news.entity.DailyNews;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_news_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class UserNewsHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_news_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_id", nullable = false)
    private DailyNews news;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    @Builder
    public UserNewsHistory(DailyNews news, User user, LocalDateTime viewdAt) {
        this.news = news;
        this.user = user;
        this.viewdAt = viewdAt;
    }

}
