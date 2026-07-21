package com.solux31.nubee_BE.domain.words.exception.code;

import com.solux31.nubee_BE.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WordsSuccessCode implements BaseSuccessCode {

    TODAY_KEYWORD_LIST_GET_SUCCESS(HttpStatus.OK, "WORDS200_1", "오늘의 키워드 및 뉴스 목록 조회에 성공했습니다."),
    KEYWORD_DETAIL_GET_SUCCESS(HttpStatus.OK, "WORDS200_2", "키워드 상세정보 조회에 성공했습니다."),
    KEYWORD_QUIZ_GET_SUCCESS(HttpStatus.OK, "WORDS200_3", "퀴즈 및 보기 문항 조회에 성공했습니다."),
    KEYWORD_QUIZ_GRADE_SUCCESS(HttpStatus.OK, "WORDS200_4", "퀴즈 채점 성공 및 포인트 지급이 완료되었습니다.");

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