package com.solux31.nubee_BE.domain.auth.service;

import com.solux31.nubee_BE.domain.auth.dto.Response.KakaoLoginResDTO;
import com.solux31.nubee_BE.domain.auth.entity.RefreshToken;
import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.enums.UserStatus;
import com.solux31.nubee_BE.domain.auth.repository.RefreshTokenRepository;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.global.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KakaoUserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final AuthService authService;

    @Transactional
    public KakaoLoginResDTO processKakaoLogin(String kakaoId, String nickname, String email) {

        // 기존 유저 조회 또는 신규 유저 생성
        final boolean[] isNewArr = {false}; // 배열로 람다 안에서 수정 가능하게

        User user = userRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(existingUser -> {
                            existingUser.updateKakaoId(kakaoId);
                            return existingUser; // 기존 계정 → isNew = false
                        })
                        .orElseGet(() -> {
                            isNewArr[0] = true;  // 신규 생성일 때만 true
                            return createKakaoUser(kakaoId, nickname, email);
                        }));

        boolean isNew = isNewArr[0];

        refreshTokenRepository.deleteByUser(user);

        String accessToken = jwtUtil.generateAccessToken(user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        saveRefreshToken(user, refreshToken);

        return new KakaoLoginResDTO(accessToken, refreshToken, isNew);
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