package com.solux31.nubee_BE.global.email;

import com.solux31.nubee_BE.domain.auth.enums.EmailVerificationType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class EmailVerificationEvent {
    private final String email;
    private final String code;
    private final EmailVerificationType type;
}