package com.solux31.nubee_BE.domain.news.entity.mapping;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "user_quiz_log", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_quiz", columnNames = {"user_id", "quiz_id"})
})
public class UserQuizLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id") // DB 컬럼명은 log_id로 유지하되 자바 필드명은 id로
    private Long id; // logId -> id로 변경

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false)
    private int selectedAnswer;

    @Column(nullable = false)
    private boolean isCorrect;

    @Column(nullable = false)
    private boolean isCompleted;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}