package com.solux31.nubee_BE.domain.profile.entity;

import com.solux31.nubee_BE.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_skin")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSkin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_skin_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skin_id", nullable = false)
    private Skin skin;

    @Column(name = "acquired_at", nullable = false)
    private LocalDateTime acquiredAt;

    @Builder
    public UserSkin(User user, Skin skin, LocalDateTime acquiredAt) {
        this.user = user;
        this.skin = skin;
        this.acquiredAt = acquiredAt;
    }
}
