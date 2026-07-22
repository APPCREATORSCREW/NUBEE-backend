package com.solux31.nubee_BE.domain.auth.exception.code;

import com.solux31.nubee_BE.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    // 회원가입/로그인
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH409_1", "이미 사용 중인 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH404_1", "존재하지 않는 유저입니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "AUTH400_1", "비밀번호가 일치하지 않습니다."),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH400_2", "새 비밀번호가 일치하지 않습니다."),

    // 토큰
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401_1", "유효하지 않은 Refresh Token입니다."),
    NOT_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401_2", "Refresh Token이 아닙니다."),
    TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH401_3", "존재하지 않는 Refresh Token입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH401_4", "만료된 Refresh Token입니다."),

    // 이메일 인증
    EMAIL_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH404_2", "인증 코드가 존재하지 않습니다."),
    EMAIL_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH400_3", "인증 코드가 만료되었습니다."),
    EMAIL_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH400_4", "인증 코드가 일치하지 않습니다."),
    EMAIL_CODE_EXCEED_FAIL(HttpStatus.BAD_REQUEST, "AUTH400_5", "인증 코드 시도 횟수를 초과했습니다."),
    EMAIL_CODE_EXCEED_SEND(HttpStatus.BAD_REQUEST, "AUTH400_6", "인증 코드 발송 횟수를 초과했습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "AUTH403_1", "이메일 인증이 완료되지 않았습니다."),
    PARENT_EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "AUTH403_2", "부모님 이메일 인증이 완료되지 않았습니다."),


    // 카카오
    KAKAO_INVALID_STATE(HttpStatus.BAD_REQUEST, "AUTH400_7", "유효하지 않은 state 값입니다."),
    KAKAO_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "AUTH401_5", "카카오 인증 코드가 유효하지 않습니다."),
    KAKAO_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH500_1", "카카오 서버 오류가 발생했습니다."),
    KAKAO_INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401_6", "유효하지 않은 카카오 Access Token입니다."),

    // 기본 스킨
    DEFAULT_SKIN_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH404_3", "기본 스킨이 존재하지 않습니다."),

    SAME_AS_OLD_PASSWORD(HttpStatus.BAD_REQUEST, "AUTH400_8", "현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다."),
    NAME_EMAIL_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH400_9", "이름 또는 이메일이 일치하지 않습니다.");

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