package com.solux31.nubee_BE.domain.words.exception;

import com.solux31.nubee_BE.global.apiPayload.code.BaseErrorCode;
import com.solux31.nubee_BE.global.apiPayload.exception.ProjectException;

public class WordsException extends ProjectException {

    public WordsException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}