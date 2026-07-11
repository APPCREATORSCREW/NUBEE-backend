package com.solux31.nubee_BE.domain.auth.service;

import com.solux31.nubee_BE.domain.auth.dto.Response.KakaoLoginResDTO;
import com.solux31.nubee_BE.domain.auth.entity.RefreshToken;
import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.enums.UserStatus;
import com.solux31.nubee_BE.domain.auth.repository.RefreshTokenRepository;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.domain.auth.dto.Response.LoginResDTO;
import com.solux31.nubee_BE.global.security.util.JwtUtil;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KakaoAuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final AuthService authService;

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
    @Transactional
    public KakaoLoginResDTO kakaoLogin(String code, String state, HttpSession session) {
        // state 검증
        String savedState = (String) session.getAttribute("kakao_state");
        if (savedState == null || !savedState.equals(state)) {
            throw new IllegalArgumentException("유효하지 않은 state 값입니다.");
        }
        session.removeAttribute("kakao_state");

        // 1. 인가 코드로 카카오 액세스 토큰 발급
        String kakaoAccessToken = getKakaoAccessToken(code);

        // 2. 카카오 액세스 토큰으로 유저 정보 조회
        Map<String, Object> userInfo = getKakaoUserInfo(kakaoAccessToken);

        // 3. 유저 정보 추출
        String kakaoId = String.valueOf(userInfo.get("id"));
        Map<String, Object> kakaoAccount = (Map<String, Object>) userInfo.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
        String nickname = (String) profile.get("nickname");
        String email = (String) kakaoAccount.get("email");

        // 4. DB 작업은 별도 트랜잭션 메서드로 분리
        return processKakaoLogin(kakaoId, nickname, email);
    }

    // DB 작업만 트랜잭션으로 처리
    @Transactional
    public KakaoLoginResDTO processKakaoLogin(String kakaoId, String nickname, String email) {

        // 신규 유저 여부 확인
        boolean isNew = !userRepository.existsByKakaoId(kakaoId);

        // 기존 유저 조회 또는 신규 유저 생성
        User user = userRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> createKakaoUser(kakaoId, nickname, email));

        // 기존 RefreshToken 삭제
        refreshTokenRepository.deleteByUser(user);

        // JWT 토큰 발급
        String accessToken = jwtUtil.generateAccessToken(user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        // RefreshToken 저장
        saveRefreshToken(user, refreshToken);

        return new KakaoLoginResDTO(accessToken, refreshToken, isNew);
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
                .bodyToMono(Map.class)
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
                .bodyToMono(Map.class)
                .block();
    }

    // 신규 카카오 유저 생성
    private User createKakaoUser(String kakaoId, String nickname, String email) {
        User user = User.builder()
                .kakaoId(kakaoId)
                .username(nickname)
                .email(email != null ? email : "kakao_" + kakaoId + "@nubee.com")
                .preferredKeywordCount(3)
                .currentSkin("DEFAULT")
                .status(UserStatus.ACTIVE)
                .isParentVerified(false)
                .build();
        return userRepository.save(user);
    }

    // RefreshToken 저장
    private void saveRefreshToken(User user, String refreshToken) {
        String hashedToken = authService.hashToken(refreshToken);
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(hashedToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(token);
    }
}