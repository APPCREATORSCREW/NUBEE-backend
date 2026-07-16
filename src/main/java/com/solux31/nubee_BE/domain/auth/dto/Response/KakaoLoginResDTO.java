package com.solux31.nubee_BE.domain.auth.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class KakaoLoginResDTO {
    private String accessToken;
    private String refreshToken;
    private boolean isNew;
}