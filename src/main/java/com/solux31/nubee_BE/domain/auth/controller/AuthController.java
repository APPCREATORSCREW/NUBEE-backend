package com.solux31.nubee_BE.domain.auth.controller;

import com.solux31.nubee_BE.domain.auth.dto.Request.LoginReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.SignupReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.TokenRefreshReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Response.LoginResDTO;
import com.solux31.nubee_BE.domain.auth.dto.Response.SignupResDTO;
import com.solux31.nubee_BE.domain.auth.dto.Response.TokenRefreshResDTO;
import com.solux31.nubee_BE.domain.auth.service.AuthService;
import com.solux31.nubee_BE.global.apiPayload.ApiResponse;
import com.solux31.nubee_BE.global.apiPayload.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증/인가 API")
public class AuthController {

    private final AuthService authService;

    // 회원가입
    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "이름, 이메일, 비밀번호, 생년월일로 회원가입합니다.")
    public ApiResponse<SignupResDTO> signup(@RequestBody @Valid SignupReqDTO request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, authService.signup(request));
    }

    // 로그인
    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인합니다.")
    public ApiResponse<LoginResDTO> login(@RequestBody @Valid LoginReqDTO request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, authService.login(request));
    }

    // 로그아웃
    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "로그아웃합니다.")
    public ApiResponse<String> logout(@AuthenticationPrincipal UserDetails userDetails) {
        authService.logout(userDetails.getUsername());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "로그아웃 되었습니다.");
    }

    // 토큰 갱신
    @PostMapping("/token/refresh")
    @Operation(summary = "토큰 갱신", description = "Refresh Token으로 새로운 Access Token을 발급")
    public ApiResponse<TokenRefreshResDTO> refresh(@RequestBody @Valid TokenRefreshReqDTO request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, authService.refresh(request));
    }
}