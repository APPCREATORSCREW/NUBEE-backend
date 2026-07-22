package com.solux31.nubee_BE.domain.profile.dto.Response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SkinApplyResDTO {
    private Long currentSkinId;
    private String currentSkinName;
}

