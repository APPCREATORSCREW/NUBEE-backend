package com.solux31.nubee_BE.domain.news.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "user_quiz_log", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_quiz", columnNames = {"userId", "quizId"})
}) //제약 조건 추가, 같은 뮈즈 중복 제출 방지
public class UserQuizLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;

    @Column(nullable = false)
    private Long userId; // 문제를 푼 유저 ID

    @Column(nullable = false)
    private Long quizId; // 어떤 퀴즈를 풀었는지

    @Column(nullable = false)
    private int selectedAnswer; // 아이가 실제 마우스로 고른 번호

    @Column(nullable = false)
    private boolean isCorrect; // 맞췄는지 여부 (true/false)

    @Column(nullable = false)
    private boolean isCompleted;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
