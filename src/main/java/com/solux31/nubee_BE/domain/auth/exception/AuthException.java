package com.solux31.nubee_BE.domain.auth.exception;

import com.solux31.nubee_BE.global.apiPayload.code.BaseErrorCode;
import com.solux31.nubee_BE.global.apiPayload.exception.ProjectException;

public class AuthException extends ProjectException {
    public AuthException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}