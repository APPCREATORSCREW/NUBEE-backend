package com.solux31.nubee_BE.domain.auth.dto.Request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class SignupReqDTO {

    private String username;
    private String email;
    private String password;
    private String passwordConfirm;
    private LocalDate birthDate;
    private int preferredKeywordCount;
    private String parentEmail;
}