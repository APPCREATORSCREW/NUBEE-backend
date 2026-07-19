package com.solux31.nubee_BE.domain.profile.exception;

import com.solux31.nubee_BE.global.apiPayload.code.BaseErrorCode;
import com.solux31.nubee_BE.global.apiPayload.exception.ProjectException;

public class ProfileException extends ProjectException {
    public ProfileException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
