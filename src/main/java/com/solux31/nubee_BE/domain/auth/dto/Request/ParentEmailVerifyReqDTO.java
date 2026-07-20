package com.solux31.nubee_BE.domain.auth.dto.Request;

// # 부모님 이메일 인증 확인

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ParentEmailVerifyReqDTO {

    @NotBlank(message = "부모님 이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String parentEmail;

    @NotBlank(message = "유저 이메일은 필수입니다.")
    @Email
    private String userEmail;

    @NotBlank(message = "인증 코드는 필수입니다.")
    private String code;
}