package com.solux31.nubee_BE.domain.auth.dto.Request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class SignupReqDTO {

    @NotBlank(message = "이름은 필수입니다.")
    private String username;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[!@#$%^&*]).{10,}$",
            message = "비밀번호는 영문, 숫자, 특수문자를 포함한 10자 이상이어야 합니다.")
    private String password;

    @NotBlank(message = "비밀번호 확인은 필수입니다.")
    private String passwordConfirm;

}