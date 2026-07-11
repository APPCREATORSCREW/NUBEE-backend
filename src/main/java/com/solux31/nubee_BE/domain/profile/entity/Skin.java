package com.solux31.nubee_BE.domain.profile.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "skin")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Skin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "skin_id")
    private Long id;

    @Column(name = "skin_code", length = 50, nullable = false, unique = true)
    private String skinCode;

    @Column(name = "skin_name", nullable = false)
    private String skinName;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "required_level", nullable = false)
    private int requiredLevel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Skin(String skinCode, String skinName, String imageUrl, int requiredLevel) {
        this.skinCode = skinCode;
        this.skinName = skinName;
        this.imageUrl = imageUrl;
        this.requiredLevel = requiredLevel;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
