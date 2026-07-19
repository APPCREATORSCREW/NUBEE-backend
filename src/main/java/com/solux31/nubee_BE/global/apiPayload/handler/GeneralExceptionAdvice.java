package com.solux31.nubee_BE.global.apiPayload.handler;

import com.solux31.nubee_BE.global.apiPayload.ApiResponse;
import com.solux31.nubee_BE.global.apiPayload.code.BaseErrorCode;
import com.solux31.nubee_BE.global.apiPayload.code.GeneralErrorCode;
import com.solux31.nubee_BE.global.apiPayload.exception.ProjectException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GeneralExceptionAdvice {

    // 프로젝트에서 발생한 예외 처리
    @ExceptionHandler(ProjectException.class)
    public ResponseEntity<ApiResponse<Void>> handleMemberException(
            ProjectException e
    ) {
        BaseErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.onFailure(errorCode, null));
    }

    // @Valid 검증 실패 예외 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<String>> handleValidException(
            MethodArgumentNotValidException e
    ) {
        // 검증 실패한 필드들의 메시지를 모아서 반환
        String errorMessage = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        BaseErrorCode code = GeneralErrorCode.BAD_REQUEST;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.onFailure(code, errorMessage));
    }

    // IllegalArgumentException 예외 처리 (서비스에서 던지는 예외)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<String>> handleIllegalArgumentException(
            IllegalArgumentException e
    ) {
        BaseErrorCode code = GeneralErrorCode.BAD_REQUEST;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.onFailure(code, e.getMessage()));
    }

    // DB 유니크 제약 위반 예외 처리 (중복 이메일 등)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<String>> handleDataIntegrityViolationException(
            DataIntegrityViolationException e
    ) {
        BaseErrorCode code = GeneralErrorCode.CONFLICT;  // 409
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.onFailure(code, "이미 사용 중인 이메일입니다."));
    }

    //동시성 문제 예외 처리
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<String>> handleOptimisticLock(
            ObjectOptimisticLockingFailureException e
    ) {
        BaseErrorCode code = GeneralErrorCode.CONFLICT;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.onFailure(code, "요청이 몰려 처리에 실패했습니다. 다시 시도해주세요."));
    }

    // 그 외의 정의되지 않은 모든 예외 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleException(
            Exception ex
    ) {
        BaseErrorCode code = GeneralErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.onFailure(
                                code,
                                ex.getMessage()
                        )
                );
    }
}