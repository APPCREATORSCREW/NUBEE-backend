package com.solux31.nubee_BE.domain.auth.dto.Request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LoginReqDTO {

    private String email;
    private String password;
}