package com.solux31.nubee_BE.domain.words.entity.mapping;

import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.words.entity.Keyword;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_keyword")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_keyword_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "keyword_id", nullable = false)
    private Keyword keyword;

    @Builder.Default
    @Column(name = "is_bookmarked", nullable = false)
    private boolean isBookmarked = false;

    @Builder.Default
    @Column(name = "is_learned", nullable = false)
    private boolean isLearned = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}