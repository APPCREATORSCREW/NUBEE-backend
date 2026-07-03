package com.solux31.nubee_BE.domain.auth.service;

import com.solux31.nubee_BE.domain.auth.dto.Request.LoginReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.SignupReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Response.LoginResDTO;
import com.solux31.nubee_BE.domain.auth.dto.Response.SignupResDTO;
import com.solux31.nubee_BE.domain.auth.entity.RefreshToken;
import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.enums.UserStatus;
import com.solux31.nubee_BE.domain.auth.repository.RefreshTokenRepository;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.global.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // 회원가입
    @Transactional
    public SignupResDTO signup(SignupReqDTO request) {

        // 이메일 중복 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 비밀번호 확인
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 만 14세 미만 확인
        int age = Period.between(request.getBirthDate(), LocalDate.now()).getYears();
        boolean isUnder14 = age < 14;

        // 만 14세 미만인데 부모님 이메일 없으면 에러
        if (isUnder14 && (request.getParentEmail() == null || request.getParentEmail().isBlank())) {
            throw new IllegalArgumentException("만 14세 미만은 부모님 이메일 인증이 필요합니다.");
        }

        // User 생성
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .birthDate(request.getBirthDate())
                .preferredKeywordCount(request.getPreferredKeywordCount())
                .parentEmail(isUnder14 ? request.getParentEmail() : null)
                .isParentVerified(false)
                .currentSkin("DEFAULT")
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(user);

        // 토큰 발급
        String accessToken = jwtUtil.generateAccessToken(user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        // RefreshToken 저장
        saveRefreshToken(user, refreshToken);

        return new SignupResDTO(accessToken, refreshToken);
    }

    // 로그인
    @Transactional
    public LoginResDTO login(LoginReqDTO request) {

        // 이메일로 유저 찾기
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이메일입니다."));

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 기존 RefreshToken 삭제
        refreshTokenRepository.deleteByUser(user);

        // 토큰 발급
        String accessToken = jwtUtil.generateAccessToken(user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        // RefreshToken 저장
        saveRefreshToken(user, refreshToken);

        return new LoginResDTO(accessToken, refreshToken);
    }

    // 로그아웃
    @Transactional
    public void logout(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        // RefreshToken 삭제
        refreshTokenRepository.deleteByUser(user);
    }

    // RefreshToken 저장 공통 메서드
    private void saveRefreshToken(User user, String refreshToken) {
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshToken)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        refreshTokenRepository.save(token);
    }
}