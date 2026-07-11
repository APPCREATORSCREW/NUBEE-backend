package com.solux31.nubee_BE.domain.profile.controller;

import com.solux31.nubee_BE.domain.profile.dto.ProfileReqDTO;
import com.solux31.nubee_BE.domain.profile.dto.ProfileResDTO;
import com.solux31.nubee_BE.domain.profile.exception.code.ProfileSuccessCode;
import com.solux31.nubee_BE.domain.profile.service.ProfileService;
import com.solux31.nubee_BE.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class ProfileController {
    private final ProfileService profileService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileResDTO.Profile>> getProfile(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        ProfileResDTO.Profile profile = profileService.getProfile(authUser.getUserId());
        return ResponseEntity.ok(
                ApiResponse.onSuccess(ProfileSuccessCode.PROFILE_FETCH_SUCCESS, profile)
        );
    }

    @PatchMapping("/settings")
    public ResponseEntity<ApiResponse<ProfileResDTO.Settings>> updateSettings(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody ProfileReqDTO.SettingUpdate request
    ) {
        ProfileResDTO.Settings settings = profileService.updateSettings(authUser.getUserId(), request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(ProfileSuccessCode.SETTINGS_UPDATE_SUCCESS, settings)
        );
    }

    @PatchMapping("/skin")
    public ResponseEntity<ApiResponse<ProfileResDTO.SkinApply>> applySkin(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody ProfileReqDTO.SkinApply request
    ) {
        ProfileResDTO.SkinApply result = profileService.applySkin(authUser.getUserId(), request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess((ProfileSuccessCode.SKIN_APPLY_SUCCESS, result)
        );
    }
}
