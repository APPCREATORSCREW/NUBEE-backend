package com.solux31.nubee_BE.domain.profile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ProfileReqDTO {

    @Getter
    @NoArgsConstructor
    public static class SettingUpdate {
        @Min(0)
        @Max(6)
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
