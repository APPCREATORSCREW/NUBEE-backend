package com.solux31.nubee_BE.domain.auth.dto.Request;

// # 부모님 이메일 인증 코드 발송

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ParentEmailSendReqDTO {

    @NotBlank(message = "부모님 이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String parentEmail;
}