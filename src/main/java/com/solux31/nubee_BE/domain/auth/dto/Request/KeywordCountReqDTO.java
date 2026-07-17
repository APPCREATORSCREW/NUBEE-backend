package com.solux31.nubee_BE.domain.auth.dto.Request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KeywordCountReqDTO {

    @NotNull(message = "키워드 개수는 필수입니다.")
    @Min(value = 3, message = "키워드 개수는 최소 3개입니다.")
    @Max(value = 6, message = "키워드 개수는 최대 6개입니다.")
    private int preferredKeywordCount;
}