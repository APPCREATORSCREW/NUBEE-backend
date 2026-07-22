package com.solux31.nubee_BE.domain.profile.dto.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SkinApplyReqDTO {
    @NotNull
    @Positive
    private Long skinId;
}
