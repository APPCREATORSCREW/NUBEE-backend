package com.solux31.nubee_BE.domain.profile.dto.Response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PresignedUrlResDTO {
    private String uploadUrl;   // 프론트가 PUT 요청 보낼 임시 URL
    private String fileUrl;     // 업로드 완료 후 최종 이미지 URL (이걸 나중에 profile-image API에 넣음)
}
