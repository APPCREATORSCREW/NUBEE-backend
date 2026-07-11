package com.solux31.nubee_BE.domain.profile.exception.code;

import com.solux31.nubee_BE.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProfileErrorCode implements BaseErrorCode {
    SKIN_NOT_OWNED(HttpStatus.BAD_REQUEST, "PROFILE400", "보유하지 않은 스킨입니다."),
    SKIN_NOT_FOUND(HttpStatus.BAD_REQUEST, "PROFILE401", "존재하지 않는 스킨입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
