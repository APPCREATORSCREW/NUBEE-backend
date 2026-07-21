package com.solux31.nubee_BE.domain.news.exception.code;

import com.solux31.nubee_BE.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NewsSuccessCode implements BaseSuccessCode {

    TODAY_NEWS_SUMMARY_GET_SUCCESS(HttpStatus.OK, "NEWS200_1", "오늘의 뉴스 요약 및 단어 목록 조회에 성공했습니다."),
    NEWS_QUIZ_GET_SUCCESS(HttpStatus.OK, "NEWS200_2", "뉴스 퀴즈 및 보기 문항 조회에 성공했습니다."),
    NEWS_QUIZ_GRADE_SUCCESS(HttpStatus.OK, "NEWS200_3", "뉴스 퀴즈 채점, 포인트 지급 및 최종 완료 처리에 성공했습니다.");

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