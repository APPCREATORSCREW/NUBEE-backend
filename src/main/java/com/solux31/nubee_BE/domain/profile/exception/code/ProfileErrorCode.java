package com.solux31.nubee_BE.domain.profile.exception.code;

import com.solux31.nubee_BE.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProfileErrorCode implements BaseErrorCode {
    SKIN_NOT_OWNED(HttpStatus.BAD_REQUEST, "PROFILE400", "보유하지 않은 스킨입니다."),
    SKIN_NOT_FOUND(HttpStatus.BAD_REQUEST, "PROFILE401", "존재하지 않는 스킨입니다."),
    INVALID_FILE_NAME(HttpStatus.BAD_REQUEST, "PROFILE402", "유효하지 않거나 너무 긴 파일명입니다."),
    INVALID_FILE_EXTENSION(HttpStatus.BAD_REQUEST, "PROFILE403", "유효하지 않은 파일 확장자입니다."),
    INVALID_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "PROFILE404", "허용되지 않는 파일 형식(Content-Type)입니다."),
    EXCEEDED_MAX_FILE_SIZE(HttpStatus.BAD_REQUEST, "PROFILE405", "파일 크기가 최대 허용 용량을 초과했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
