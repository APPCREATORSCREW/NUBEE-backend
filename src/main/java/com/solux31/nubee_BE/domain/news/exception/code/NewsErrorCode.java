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
    INVALID_URL_PROTOCOL(HttpStatus.BAD_REQUEST, "NEWS400_4", "http 또는 https 프로토콜만 허용됩니다."),
    INVALID_URL_HOST(HttpStatus.BAD_REQUEST, "NEWS400_5", "올바르지 않거나 접근이 제한된 URL 주소입니다."),

    // --- 404 Not Found ---
    DAILY_NEWS_NOT_FOUND(HttpStatus.NOT_FOUND, "NEWS404_1", "오늘 제공된 뉴스 학습 데이터가 존재하지 않습니다."),
    NEWS_NOT_FOUND(HttpStatus.NOT_FOUND, "NEWS404_2", "존재하지 않는 뉴스 기사입니다."),
    NEWS_QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "NEWS404_3", "해당 뉴스의 퀴즈가 존재하지 않습니다."),

    // --- 500 Internal Server Error ---
    ARTICLE_BODY_EMPTY(HttpStatus.INTERNAL_SERVER_ERROR, "NEWS500_1", "기사 본문을 추출할 수 없거나 비어있습니다."),
    GEMINI_ANALYSIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "NEWS500_2", "AI 분석 결과 생성 중 오류가 발생했습니다."),
    GEMINI_PARSE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "NEWS500_3", "AI 응답 데이터 파싱에 실패했습니다.");

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