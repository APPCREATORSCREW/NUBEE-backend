package com.solux31.nubee_BE.domain.auth.entity;

import com.solux31.nubee_BE.domain.auth.enums.EmailVerificationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "code", nullable = false, length = 6)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private EmailVerificationType type;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "fail_count", nullable = false)
    @Builder.Default
    private int failCount = 0;

    @Column(name = "send_count", nullable = false)
    @Builder.Default
    private int sendCount = 1;

    // 실패 횟수 증가
    public void increaseFailCount() {
        this.failCount++;
    }

    // 발송 횟수 증가
    public void increaseSendCount() {
        this.sendCount++;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 인증 완료 처리
    public void verify() {
        this.isVerified = true;
    }
}