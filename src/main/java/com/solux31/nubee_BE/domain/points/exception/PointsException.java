package com.solux31.nubee_BE.domain.points.exception;

import com.solux31.nubee_BE.global.apiPayload.code.BaseErrorCode;
import com.solux31.nubee_BE.global.apiPayload.exception.ProjectException;

public class PointsException extends ProjectException {
    public PointsException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}
