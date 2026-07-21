package com.solux31.nubee_BE.domain.words.exception.code;

import com.solux31.nubee_BE.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WordsErrorCode implements BaseErrorCode {

    // --- 400 Bad Request ---
    INVALID_USER_INFO(HttpStatus.BAD_REQUEST, "WORDS400_1", "유저 정보 식별에 실패했습니다."),
    INVALID_KEYWORD_ID(HttpStatus.BAD_REQUEST, "WORDS400_2", "유효하지 않은 키워드 ID입니다."),
    INVALID_QUIZ_REQUEST(HttpStatus.BAD_REQUEST, "WORDS400_3", "필수 입력값이 누락되었거나 이미 완료된 키워드 퀴즈입니다."),

    // --- 404 Not Found ---
    KEYWORD_NOT_FOUND(HttpStatus.NOT_FOUND, "WORDS404_1", "존재하지 않는 키워드입니다."),
    KEYWORD_QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "WORDS404_2", "해당 키워드에 연결된 퀴즈가 존재하지 않습니다."),

    // --- 500 Not Found ---
    GEMINI_EMPTY_RESULT(HttpStatus.INTERNAL_SERVER_ERROR, "WORDS500_1", "Gemini 분석 결과가 비어있어 키워드 및 퀴즈를 적재할 수 없습니다.");

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