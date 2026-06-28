package com.solux31.nubee_BE.global.apiPayload.exception;

import com.solux31.nubee_BE.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ProjectException extends RuntimeException {
  private final BaseErrorCode errorCode;
}
