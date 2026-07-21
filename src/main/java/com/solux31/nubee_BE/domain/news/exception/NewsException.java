package com.solux31.nubee_BE.domain.news.exception;

import com.solux31.nubee_BE.global.apiPayload.code.BaseErrorCode;
import com.solux31.nubee_BE.global.apiPayload.exception.ProjectException;

public class NewsException extends ProjectException {

    public NewsException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}