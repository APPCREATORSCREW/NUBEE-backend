package com.solux31.nubee_BE.domain.profile.dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PresignedUrlReqDTO {
    @NotBlank
    private String fileName;

    @NotBlank
    private String contentType;

    @NotNull
    private Long contentLength;
}
