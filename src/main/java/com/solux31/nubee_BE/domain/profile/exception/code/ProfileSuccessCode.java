package com.solux31.nubee_BE.domain.profile.exception.code;

import com.solux31.nubee_BE.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ProfileSuccessCode implements BaseSuccessCode {
    PROFILE_FETCH_SUCCESS(HttpStatus.OK, "PROFILE200", "프로필 조회 성공"),
    SETTINGS_FETCH_SUCCESS(HttpStatus.OK, "PROFILE201", "학습 설정 조회 성공"),
    SETTINGS_UPDATE_SUCCESS(HttpStatus.OK, "PROFILE202", "학습 설정 수정 성공"),
    SKIN_APPLY_SUCCESS(HttpStatus.OK, "PROFILE203", "스킨 적용 성공"),
    PROFILE_IMAGE_UPDATE_SUCCESS(HttpStatus.OK, "PROFILE204", "프로필 이미지 변경 성공"),
    PRESIGNED_URL_SUCCESS(HttpStatus.OK, "PROFILE205", "presigned URL 발급 성공");

    private final HttpStatus status;
    private final String code;
    private final String message;

    @Override
    public HttpStatus getStatus() {
        return status;
    }
}
