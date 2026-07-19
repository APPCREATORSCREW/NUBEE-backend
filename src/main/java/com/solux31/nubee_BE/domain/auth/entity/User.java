package com.solux31.nubee_BE.domain.auth.entity;

import com.solux31.nubee_BE.domain.auth.enums.UserStatus;
import com.solux31.nubee_BE.domain.profile.entity.UserSkin;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE user_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "kakao_id", unique = true)
    private String kakaoId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Builder.Default
    @Column(name = "current_point", nullable = false)
    private int point = 0;

    @Builder.Default
    @Column(name = "current_level", nullable = false)
    private int currentLevel = 1;

    @Builder.Default
    @Column(name = "preferred_keyword_count", nullable = false)
    private int preferredKeywordCount = 3;

    @Column(name = "notification_time")
    private LocalTime notificationTime;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_skin_id")
    private UserSkin currentSkin;

    @Column(name = "parent_email")
    private String parentEmail;

    @Builder.Default
    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled = false;

    @Builder.Default
    @Column(name = "is_parent_verified", nullable = false)
    private boolean isParentVerified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 포인트 업데이트
    public void updatePoint(int point) {
        this.point += point;
    }

    // 레벨 업데이트
    public void updateLevel(int level) {
        this.currentLevel = level;
    }

    // 스킨 업데이트
    public void updateCurrentSkin(UserSkin skin) {
        this.currentSkin = skin;
    }

    // 키워드 개수 업데이트
    public void updatePreferredKeywordCount(int count) {
        this.preferredKeywordCount = count;
    }

    // 비밀번호 변경
    public void updatePassword(String password) {
        this.password = password;
    }

    // 같은 이메일로 가입된 계정이 있으면 kakaoId 연결
    public void updateKakaoId(String kakaoId) {
        this.kakaoId = kakaoId;
    }

    public void updateNotificationSettings(boolean enabled, LocalTime time) {
        this.notificationEnabled = enabled;
        this.notificationTime = time;
    }

    public void updateBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }
}