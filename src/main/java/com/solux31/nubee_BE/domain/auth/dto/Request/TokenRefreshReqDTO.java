package com.solux31.nubee_BE.domain.auth.dto.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Getter
@NoArgsConstructor
public class TokenRefreshReqDTO {

    @NotBlank(message = "Refresh Token은 필수입니다.")
    private String refreshToken;
}