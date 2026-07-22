package com.solux31.nubee_BE.domain.profile.controller;

import com.solux31.nubee_BE.domain.profile.dto.Request.ProfileImageUpdateReqDTO;
import com.solux31.nubee_BE.domain.profile.dto.Request.SettingUpdateReqDTO;
import com.solux31.nubee_BE.domain.profile.dto.Request.SkinApplyReqDTO;
import com.solux31.nubee_BE.domain.profile.dto.Response.ProfileImageResDTO;
import com.solux31.nubee_BE.domain.profile.dto.Response.ProfileResDTO;
import com.solux31.nubee_BE.domain.profile.dto.Response.SettingsResDTO;
import com.solux31.nubee_BE.domain.profile.dto.Response.SkinApplyResDTO;
import com.solux31.nubee_BE.domain.profile.exception.code.ProfileSuccessCode;
import com.solux31.nubee_BE.domain.profile.service.ProfileService;
import com.solux31.nubee_BE.global.apiPayload.ApiResponse;
import com.solux31.nubee_BE.global.security.entity.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "프로필", description = "프로필 조회 및 설정 관련 API")
@SecurityRequirement(name = "JWT TOKEN")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @Operation(summary = "프로필 조회", description = "로그인한 유저의 프로필 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로필 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileResDTO>> getProfile(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        ProfileResDTO profile = profileService.getProfile(authUser.getUserId());
        return ResponseEntity.ok(
                ApiResponse.onSuccess(ProfileSuccessCode.PROFILE_FETCH_SUCCESS, profile)
        );
    }

    @Operation(summary = "학습 설정 조회", description = "로그인한 유저의 학습 설정을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "학습 설정 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsResDTO>> getSettings(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        SettingsResDTO settings = profileService.getSettings(authUser.getUserId());
        return ResponseEntity.ok(
                ApiResponse.onSuccess(ProfileSuccessCode.SETTINGS_FETCH_SUCCESS, settings)
        );
    }

    @Operation(summary = "학습 설정 수정", description = "키워드 개수, 알림 여부/시간을 수정합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "학습 설정 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PatchMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsResDTO>> updateSettings(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody SettingUpdateReqDTO request
    ) {
        SettingsResDTO settings = profileService.updateSettings(authUser.getUserId(), request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(ProfileSuccessCode.SETTINGS_UPDATE_SUCCESS, settings)
        );
    }

    @Operation(summary = "스킨 적용", description = "보유한 스킨 중에서만 선택 가능합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "스킨 적용 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "보유하지 않은 스킨"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PatchMapping("/skin")
    public ResponseEntity<ApiResponse<SkinApplyResDTO>> applySkin(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody SkinApplyReqDTO request
    ) {
        SkinApplyResDTO result = profileService.applySkin(authUser.getUserId(), request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(ProfileSuccessCode.SKIN_APPLY_SUCCESS, result)
        );
    }

    @Operation(summary = "프로필 이미지 변경", description = "유저가 선택한 이미지의 URL을 받아 프로필 이미지를 변경합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로필 이미지 변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PatchMapping("/profile-image")
    public ResponseEntity<ApiResponse<ProfileImageResDTO>> updateProfileImage(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody ProfileImageUpdateReqDTO request
    ) {
        ProfileImageResDTO result = profileService.updateProfileImage(authUser.getUserId(), request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(ProfileSuccessCode.PROFILE_IMAGE_UPDATE_SUCCESS, result)
        );
    }
}