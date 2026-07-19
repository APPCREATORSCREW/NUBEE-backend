package com.solux31.nubee_BE.domain.profile.entity;

import com.solux31.nubee_BE.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "user_streak")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserStreak {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "streak_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "achieved_date", nullable = false)
    private LocalDate achievedDate;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Builder
    public UserStreak(User user, LocalDate achievedDate, int currentStreak) {
        this.user = user;
        this.achievedDate = achievedDate;
        this.currentStreak = currentStreak;
    }
}
