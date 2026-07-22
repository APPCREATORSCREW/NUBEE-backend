package com.solux31.nubee_BE.domain.profile.dto.Response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SkinInfoResDTO {
    private Long skinId;
    private String skinName;
    private String imageUrl;
    private boolean isOwned;
}
