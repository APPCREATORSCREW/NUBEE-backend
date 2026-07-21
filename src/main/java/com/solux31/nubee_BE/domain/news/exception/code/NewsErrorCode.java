package com.solux31.nubee_BE.domain.news.exception.code;

import com.solux31.nubee_BE.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NewsErrorCode implements BaseErrorCode {

    // --- 400 Bad Request ---
    INVALID_USER_INFO(HttpStatus.BAD_REQUEST, "NEWS400_1", "유저 정보 식별에 실패했습니다."),
    INVALID_NEWS_ID(HttpStatus.BAD_REQUEST, "NEWS400_2", "유효하지 않은 뉴스 ID입니다."),
    INVALID_QUIZ_REQUEST(HttpStatus.BAD_REQUEST, "NEWS400_3", "필수 입력값이 누락되었거나 이미 완료된 뉴스 퀴즈입니다."),

    // --- 404 Not Found ---
    DAILY_NEWS_NOT_FOUND(HttpStatus.NOT_FOUND, "NEWS404_1", "오늘 제공된 뉴스 학습 데이터가 존재하지 않습니다."),
    NEWS_NOT_FOUND(HttpStatus.NOT_FOUND, "NEWS404_2", "존재하지 않는 뉴스 기사입니다."),
    NEWS_QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "NEWS404_3", "해당 뉴스의 퀴즈가 존재하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    @Override
    public HttpStatus getStatus() { return status; }

    @Override
    public String getCode() { return code; }

    @Override
    public String getMessage() { return message; }
}