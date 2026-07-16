package com.solux31.nubee_BE.domain.profile.controller;

import com.solux31.nubee_BE.domain.profile.dto.ProfileReqDTO;
import com.solux31.nubee_BE.domain.profile.dto.ProfileResDTO;
import com.solux31.nubee_BE.domain.profile.exception.code.ProfileSuccessCode;
import com.solux31.nubee_BE.domain.profile.service.ProfileService;
import com.solux31.nubee_BE.global.apiPayload.ApiResponse;
import com.solux31.nubee_BE.global.security.entity.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class ProfileController {
    private final ProfileService profileService;

    @Operation(
            summary = "프로필 조회",
            description = "로그인한 유저의 프로필 정보(닉네임, 이메일, 레벨, 포인트, 연속학습일수, 현재 스킨, 보유 스킨 목록)를 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로필 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패 (토큰 없음 또는 만료)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileResDTO.Profile>> getProfile(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        ProfileResDTO.Profile profile = profileService.getProfile(authUser.getUserId());
        return ResponseEntity.ok(
                ApiResponse.onSuccess(ProfileSuccessCode.PROFILE_FETCH_SUCCESS, profile)
        );
    }

    @Operation(
            summary = "학습 설정 조회",
            description = "로그인한 유저의 학습 설정(하루 학습 키워드 수, 알림 여부, 알림 시간)을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "학습 설정 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패 (토큰 없음 또는 만료)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<ProfileResDTO.Settings>> getSettings(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        ProfileResDTO.Settings settings = profileService.getSettings(authUser.getUserId());
        return ResponseEntity.ok(
                ApiResponse.onSuccess(ProfileSuccessCode.SETTINGS_FETCH_SUCCESS, settings)
        );
    }

    @Operation(
            summary = "학습 설정 수정",
            description = "하루 학습 키워드 수(최대 6개), 학습 알림 여부 및 알림 시간을 수정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "학습 설정 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 (키워드 개수 범위 초과 등)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패 (토큰 없음 또는 토큰 만료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
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

    @Operation(
            summary = "스킨 적용",
            description = "보유한 스킨 중에서만 선택 가능하며, 선택 시 User의 현재 스킨(current_skin_id)이 갱신됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "스킨 적용 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "보유하지 않은 스킨 선택 또는 존재하지 않는 스킨"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패 (토큰 없음 또는 만료)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PatchMapping("/skin")
    public ResponseEntity<ApiResponse<ProfileResDTO.SkinApply>> applySkin(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody ProfileReqDTO.SkinApply request
    ) {
        ProfileResDTO.SkinApply result = profileService.applySkin(authUser.getUserId(), request);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(ProfileSuccessCode.SKIN_APPLY_SUCCESS, result)
        );
    }
}
