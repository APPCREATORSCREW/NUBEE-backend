package com.solux31.nubee_BE.domain.points.exception.code;

import com.solux31.nubee_BE.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PointsErrorCode implements BaseErrorCode {
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "POINT400", "적립 포인트는 0 이상이어야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
