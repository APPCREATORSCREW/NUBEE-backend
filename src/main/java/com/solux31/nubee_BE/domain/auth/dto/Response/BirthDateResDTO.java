package com.solux31.nubee_BE.domain.auth.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BirthDateResDTO {

    private boolean isUnder14;  // 만 14세 미만 여부
}