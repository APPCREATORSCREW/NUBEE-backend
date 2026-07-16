package com.solux31.nubee_BE.domain.auth.service;

import com.solux31.nubee_BE.domain.auth.dto.Request.TokenRefreshReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Response.TokenRefreshResDTO;
import com.solux31.nubee_BE.domain.auth.entity.RefreshToken;
import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.enums.UserStatus;
import com.solux31.nubee_BE.domain.auth.repository.RefreshTokenRepository;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.global.security.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional  // 테스트 후 롤백
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private String validRefreshToken;

    @BeforeEach  // 각 테스트 전에 실행
    void setUp() {
        // 테스트용 유저 생성
        testUser = User.builder()
                .username("테스트용 유저")
                .email("kimdaeun0701@test.com")
                .password(passwordEncoder.encode("test1234!@"))
                .birthDate(LocalDate.of(2000, 1, 1))
                .preferredKeywordCount(3)
                .currentSkin("DEFAULT")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(testUser);

        // 테스트용 Refresh Token 생성
        validRefreshToken = jwtUtil.generateRefreshToken(testUser.getEmail());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(testUser)
                .tokenHash(hashToken(validRefreshToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(refreshToken);
    }

    @Test
    @DisplayName("토큰 갱신 성공")
    void refresh_success() {
        // given
        TokenRefreshReqDTO request = new TokenRefreshReqDTO(validRefreshToken);

        // when
        TokenRefreshResDTO response = authService.refresh(request);

        // then
        assertThat(response.getAccessToken()).isNotNull();
        assertThat(response.getRefreshToken()).isNotNull();
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token으로 갱신 실패")
    void refresh_invalidToken() {
        // given
        TokenRefreshReqDTO request = new TokenRefreshReqDTO("invalid-token");

        // when & then
        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 Refresh Token입니다.");
    }

    @Test
    @DisplayName("Access Token으로 갱신 시도 시 실패")
    void refresh_withAccessToken() {
        // given
        String accessToken = jwtUtil.generateAccessToken(testUser.getEmail());
        TokenRefreshReqDTO request = new TokenRefreshReqDTO(accessToken);

        // when & then
        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh Token이 아닙니다.");
    }

    @Test
    @DisplayName("DB에 존재하지 않는 Refresh Token으로 갱신 실패")
    void refresh_notFoundToken() {
        // given
        String unknownToken = jwtUtil.generateRefreshToken("unknown@test.com");
        TokenRefreshReqDTO request = new TokenRefreshReqDTO(unknownToken);

        // when & then
        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 Refresh Token입니다.");
    }

    @Test
    @DisplayName("만료된 Refresh Token으로 갱신 실패")
    void refresh_expiredToken() {
        // given
        // 기존 토큰 삭제
        refreshTokenRepository.deleteAll();

        // 새로운 토큰 생성 후 만료된 상태로 저장
        String expiredRefreshToken = jwtUtil.generateRefreshToken(testUser.getEmail());

        RefreshToken expiredToken = RefreshToken.builder()
                .user(testUser)
                .tokenHash(hashToken(expiredRefreshToken))
                .expiresAt(LocalDateTime.now().minusDays(1))  // 이미 만료
                .build();
        refreshTokenRepository.save(expiredToken);

        TokenRefreshReqDTO request = new TokenRefreshReqDTO(expiredRefreshToken);

        // when & then
        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("만료된 Refresh Token입니다.");
    }

    // AuthService의 hashToken과 동일한 메서드
    private String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}