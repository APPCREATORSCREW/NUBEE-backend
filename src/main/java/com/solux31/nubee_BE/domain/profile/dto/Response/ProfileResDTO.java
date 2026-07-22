package com.solux31.nubee_BE.domain.profile.dto.Response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProfileResDTO {
    private String username;
    private String email;
    private String profileImageUrl;
    private int currentLevel;
    private int currentPoint;
    private int currentStreak;
    private Long currentSkinId;
    private String currentSkinName;
    private List<SkinInfoResDTO> skins;
}
