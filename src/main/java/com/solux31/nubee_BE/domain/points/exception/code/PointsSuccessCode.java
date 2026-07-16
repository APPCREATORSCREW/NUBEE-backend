package com.solux31.nubee_BE.domain.points.exception.code;

import com.solux31.nubee_BE.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PointsSuccessCode implements BaseSuccessCode {
    POINT_FETCH_SUCCESS(HttpStatus.OK, "POINT200", "포인트 조회 성공");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
