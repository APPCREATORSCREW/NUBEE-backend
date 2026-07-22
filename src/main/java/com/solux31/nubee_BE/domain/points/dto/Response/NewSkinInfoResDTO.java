package com.solux31.nubee_BE.domain.points.dto.Response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NewSkinInfoResDTO {
    private Long skinId;
    private String skinName;
}
