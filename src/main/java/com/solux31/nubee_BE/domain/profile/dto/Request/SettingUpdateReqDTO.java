package com.solux31.nubee_BE.domain.profile.dto.Request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SettingUpdateReqDTO {
    @Min(0)
    @Max(6)
    private int preferredKeywordCount;
    private boolean notificationEnabled;
    private String notificationTime;
}
