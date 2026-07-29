package com.solux31.nubee_BE.domain.auth.service;

import com.solux31.nubee_BE.domain.auth.dto.Response.KakaoLoginResDTO;
import com.solux31.nubee_BE.domain.auth.exception.AuthException;
import com.solux31.nubee_BE.domain.auth.exception.code.AuthErrorCode;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.global.security.util.JwtUtil;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class KakaoAuthService {

    private final KakaoUserService kakaoUserService;

    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.client-secret}")
    private String clientSecret;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    @Value("${kakao.auth-url}")
    private String authUrl;

    @Value("${kakao.api-url}")
    private String apiUrl;

    // 카카오 로그인 URL 생성
    public String getKakaoLoginUrl(HttpSession session) {
        String state = UUID.randomUUID().toString();
        session.setAttribute("kakao_state", state);

        return authUrl + "/oauth/authorize"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&state=" + state;
    }

    // 카카오 로그인 처리
    @SuppressWarnings("unchecked")
    public KakaoLoginResDTO kakaoLogin(String code, String state, HttpSession session) {
        // state 검증
        String savedState = (String) session.getAttribute("kakao_state");
        if (savedState == null || !savedState.equals(state)) {
            throw new AuthException(AuthErrorCode.KAKAO_INVALID_STATE);
        }
        session.removeAttribute("kakao_state");

        // 1. 인가 코드로 카카오 액세스 토큰 발급
        String kakaoAccessToken = getKakaoAccessToken(code);

        // 2. 카카오 액세스 토큰으로 유저 정보 조회
        Map<String, Object> userInfo = getKakaoUserInfo(kakaoAccessToken);

        // 3. 유저 정보 추출
        String kakaoId = String.valueOf(userInfo.get("id"));
        Map<String, Object> kakaoAccount = userInfo.get("kakao_account") != null
                ? (Map<String, Object>) userInfo.get("kakao_account")
                : null;

        Map<String, Object> profile = (kakaoAccount != null && kakaoAccount.get("profile") != null)
                ? (Map<String, Object>) kakaoAccount.get("profile")
                : null;

        String nickname = (profile != null && profile.get("nickname") != null)
                ? (String) profile.get("nickname")
                : "카카오유저";  // 기본값

        String email = (kakaoAccount != null && kakaoAccount.get("email") != null)
                ? (String) kakaoAccount.get("email")
                : "kakao_" + kakaoId + "@nubee.com";  // 기본값

        // 4. DB 작업은 별도 트랜잭션 메서드로 분리
        return kakaoUserService.processKakaoLogin(kakaoId, nickname, email);
    }

    // 카카오 액세스 토큰 발급
    private String getKakaoAccessToken(String code) {
        WebClient webClient = WebClient.create(authUrl);

        Map response = webClient.post()
                .uri("/oauth/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue("grant_type=authorization_code"
                        + "&client_id=" + clientId
                        + "&client_secret=" + clientSecret
                        + "&redirect_uri=" + redirectUri
                        + "&code=" + code)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        res -> Mono.error(new AuthException(AuthErrorCode.KAKAO_AUTH_FAILED)))
                .onStatus(status -> status.is5xxServerError(),
                        res -> Mono.error(new AuthException(AuthErrorCode.KAKAO_SERVER_ERROR)))
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))  // 5초 타임아웃
                .block();

        return (String) response.get("access_token");
    }

    // 카카오 유저 정보 조회
    @SuppressWarnings("unchecked")
    private Map<String, Object> getKakaoUserInfo(String kakaoAccessToken) {
        WebClient webClient = WebClient.create(apiUrl);

        return webClient.get()
                .uri("/v2/user/me")
                .header("Authorization", "Bearer " + kakaoAccessToken)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        res -> Mono.error(new AuthException(AuthErrorCode.KAKAO_INVALID_ACCESS_TOKEN)))
                .onStatus(status -> status.is5xxServerError(),
                        res -> Mono.error(new AuthException(AuthErrorCode.KAKAO_SERVER_ERROR)))
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))  // 5초 타임아웃
                .block();
    }

}