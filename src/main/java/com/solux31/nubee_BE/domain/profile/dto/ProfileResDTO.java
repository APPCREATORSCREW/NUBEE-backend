package com.solux31.nubee_BE.domain.profile.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

public class ProfileResDTO {

    @Getter
    @Builder
    public static class Profile {
        private String username;
        private String email;
        private int currentLevel;
        private int currentPoint;
        private int currentStreak;
        private Long currentSkinId;
        private String currentSkinName;
        private List<SkinInfo> skins;
    }

    @Getter
    @Builder
    public static class SkinInfo {
        private Long skinId;
        private String skinName;
        private String imageUrl;
        private boolean isOwned;
    }

    @Getter
    @Builder
    public static class Settings {
        private int preferredKeywordCount;
        private boolean notificationEnabled;
        private String notificationTime;
    }

    @Getter
    @Builder
    public static class SkinApply {
        private Long currentSkinId;
        private String currentSkinName;
    }
}
