package com.solux31.nubee_BE.domain.auth.controller;

import com.solux31.nubee_BE.domain.auth.dto.Request.LoginReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.ParentEmailSendReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.ParentEmailVerifyReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.PasswordChangeReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.PasswordResetConfirmReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.PasswordResetEmailReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.PasswordResetVerifyReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.SignupReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.TokenRefreshReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Response.KakaoLoginResDTO;
import com.solux31.nubee_BE.domain.auth.dto.Response.LoginResDTO;
import com.solux31.nubee_BE.domain.auth.dto.Response.SignupResDTO;
import com.solux31.nubee_BE.domain.auth.dto.Response.TokenRefreshResDTO;
import com.solux31.nubee_BE.domain.auth.service.AuthService;
import com.solux31.nubee_BE.domain.auth.service.KakaoAuthService;
import com.solux31.nubee_BE.global.apiPayload.ApiResponse;
import com.solux31.nubee_BE.global.apiPayload.code.GeneralSuccessCode;
import com.solux31.nubee_BE.global.security.entity.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증/인가 API")
public class AuthController {

    private final AuthService authService;
    private final KakaoAuthService kakaoAuthService;

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

    // 비밀번호 변경
    @PatchMapping("/password")
    @Operation(summary = "비밀번호 변경", description = "로그인 상태에서 비밀번호를 변경")
    public ApiResponse<String> changePassword(@AuthenticationPrincipal AuthUser authUser,
                                              @RequestBody @Valid PasswordChangeReqDTO request) {
        authService.changePassword(authUser.getUsername(), request);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "비밀번호가 변경되었습니다.");
    }

    // 비밀번호 찾기 - 이메일 인증 발송
    @PostMapping("/password/reset")
    @Operation(summary = "비밀번호 찾기 - 이메일 인증 발송", description = "이름과 이메일로 인증 코드를 발송합니다.")
    public ApiResponse<String> sendPasswordResetEmail(@RequestBody @Valid PasswordResetEmailReqDTO request) {
        authService.sendPasswordResetEmail(request);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "인증 코드가 발송되었습니다.");
    }

    // 비밀번호 찾기 - 인증번호 확인
    @PostMapping("/password/reset/verify")
    @Operation(summary = "비밀번호 찾기 - 인증번호 확인", description = "인증 코드를 확인합니다.")
    public ApiResponse<String> verifyPasswordResetCode(@RequestBody @Valid PasswordResetVerifyReqDTO request) {
        authService.verifyPasswordResetCode(request);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "인증이 완료되었습니다.");
    }

    // 비밀번호 재설정
    @PatchMapping("/password/reset/confirm")
    @Operation(summary = "비밀번호 재설정", description = "인증 완료 후 비밀번호를 재설정합니다.")
    public ApiResponse<String> resetPassword(@RequestBody @Valid PasswordResetConfirmReqDTO request) {
        authService.resetPassword(request);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "비밀번호가 재설정되었습니다.");
    }

    // 부모님 이메일 인증 코드 발송
    @PostMapping("/parent/email/send")
    @Operation(summary = "부모님 이메일 인증 코드 발송", description = "만 14세 미만 회원가입 시 부모님 이메일로 인증 코드를 발송합니다.")
    public ApiResponse<String> sendParentVerifyEmail(@RequestBody @Valid ParentEmailSendReqDTO request) {
        authService.sendParentVerifyEmail(request);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "인증 코드가 발송되었습니다.");
    }

    // 부모님 이메일 인증 확인
    @PostMapping("/parent/email/verify")
    @Operation(summary = "부모님 이메일 인증 확인", description = "부모님 이메일 인증 코드를 확인합니다.")
    public ApiResponse<String> verifyParentEmail(@RequestBody @Valid ParentEmailVerifyReqDTO request) {
        authService.verifyParentEmail(request);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "인증이 완료되었습니다.");
    }

    @Value("${front.url}")
    private String frontUrl;

    // 카카오 로그인 시작
    @GetMapping("/kakao")
    @Operation(summary = "카카오 로그인 시작", description = "카카오 인증 페이지로 리다이렉트합니다.")
    public void kakaoLogin(HttpServletResponse response, HttpSession session) throws IOException {
        String kakaoLoginUrl = kakaoAuthService.getKakaoLoginUrl(session);
        response.sendRedirect(kakaoLoginUrl);
    }

    // 카카오 로그인 콜백
    @GetMapping("/kakao/callback")
    @Operation(summary = "카카오 로그인 콜백", description = "카카오 OAuth 인증 후 콜백을 처리합니다.")
    public void kakaoCallback(@RequestParam(required = false) String code,
                              @RequestParam(required = false) String state,
                              @RequestParam(required = false) String error,
                              HttpServletResponse response, HttpSession session) throws IOException {
        if (error != null) {
            response.sendRedirect(frontUrl + "/callback?error=" + URLEncoder.encode("카카오 로그인에 실패했습니다.", StandardCharsets.UTF_8));
            return;
        }

        try {
            KakaoLoginResDTO result = kakaoAuthService.kakaoLogin(code, state, session);
            response.sendRedirect(frontUrl + "/callback"
                    + "?access_token=" + URLEncoder.encode(result.getAccessToken(), StandardCharsets.UTF_8)
                    + "&refresh_token=" + URLEncoder.encode(result.getRefreshToken(), StandardCharsets.UTF_8)
                    + "&is_new=" + result.isNew());
        } catch (Exception e) {
            response.sendRedirect(frontUrl + "/callback?error="+ URLEncoder.encode("카카오 로그인 처리 중 오류가 발생했습니다.", StandardCharsets.UTF_8));
        }
    }
}