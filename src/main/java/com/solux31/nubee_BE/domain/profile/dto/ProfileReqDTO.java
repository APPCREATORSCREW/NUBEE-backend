package com.solux31.nubee_BE.domain.profile.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

public class ProfileReqDTO {

    @Getter
    @NoArgsConstructor
    public static class SettingUpdate {
        private int preferredKeywordCount;
        private boolean notificationEnabled;
        private String notificationTime;
    }

    @Getter
    @NoArgsConstructor
    public static class SkinApply {
        private Long skinId;
    }
}
