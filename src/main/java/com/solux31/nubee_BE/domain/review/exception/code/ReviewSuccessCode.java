package com.solux31.nubee_BE.domain.review.exception.code;

import com.solux31.nubee_BE.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewSuccessCode implements BaseSuccessCode {
    REVIEW_FETCH_SUCCESS(HttpStatus.OK, "REVIEW200", "뉴스 다시보기 목록 조회 성공"),
    CATEGORY_FETCH_SUCCESS(HttpStatus.OK, "REVIEW201", "카테고리 목록 조회 성공");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
