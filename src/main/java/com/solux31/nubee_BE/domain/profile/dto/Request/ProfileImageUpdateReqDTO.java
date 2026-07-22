package com.solux31.nubee_BE.domain.profile.dto.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

@Getter
@NoArgsConstructor
public class ProfileImageUpdateReqDTO {
    @NotBlank
    @URL
    private String profileImageUrl;
}
