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
    private Integer preferredKeywordCount;
    private Boolean notificationEnabled;
    private String notificationTime;
}
