package com.solux31.nubee_BE.domain.auth.service;

import com.solux31.nubee_BE.domain.auth.dto.Request.BirthDateReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.KeywordCountReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.LoginReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.ParentEmailSendReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.ParentEmailVerifyReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.PasswordChangeReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.PasswordResetConfirmReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.PasswordResetEmailReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.PasswordResetVerifyReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.SignupReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Request.TokenRefreshReqDTO;
import com.solux31.nubee_BE.domain.auth.dto.Response.BirthDateResDTO;
import com.solux31.nubee_BE.domain.auth.dto.Response.LoginResDTO;
import com.solux31.nubee_BE.domain.auth.dto.Response.SignupResDTO;
import com.solux31.nubee_BE.domain.auth.dto.Response.TokenRefreshResDTO;
import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.enums.EmailVerificationType;
import com.solux31.nubee_BE.domain.auth.enums.UserStatus;
import com.solux31.nubee_BE.domain.auth.exception.AuthException;
import com.solux31.nubee_BE.domain.auth.exception.code.AuthErrorCode;
import com.solux31.nubee_BE.domain.auth.repository.EmailVerificationRedisRepository;
import com.solux31.nubee_BE.domain.auth.repository.RefreshTokenRedisRepository;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.domain.profile.entity.Skin;
import com.solux31.nubee_BE.domain.profile.entity.mapping.UserSkin;
import com.solux31.nubee_BE.domain.profile.repository.SkinRepository;
import com.solux31.nubee_BE.domain.profile.repository.UserSkinRepository;
import com.solux31.nubee_BE.global.email.EmailService;
import com.solux31.nubee_BE.global.email.EmailVerificationEvent;
import com.solux31.nubee_BE.global.security.util.JwtUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;
    private final SkinRepository skinRepository;
    private final UserSkinRepository userSkinRepository;
    private final EmailVerificationService emailVerificationService;
    private final EmailVerificationRedisRepository emailVerificationRedisRepository;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;

    // 회원가입
    @Transactional
    public SignupResDTO signup(SignupReqDTO request) {

        // 이메일 중복 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AuthException(AuthErrorCode.DUPLICATE_EMAIL);
        }

        // 비밀번호 확인
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new AuthException(AuthErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        // User 생성
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(user);

        // 기본 스킨 조회
        Skin defaultSkin = skinRepository.findBySkinCode("DEFAULT")
                .orElseThrow(() -> new AuthException(AuthErrorCode.DEFAULT_SKIN_NOT_FOUND));

        // user_skin에 기본 스킨 지급
        UserSkin userSkin = UserSkin.builder()
                .user(user)
                .skin(defaultSkin)
                .acquiredAt(LocalDateTime.now())
                .build();
        userSkinRepository.save(userSkin);

        // current_skin_id를 기본 스킨으로 설정
        user.updateCurrentSkin(userSkin);

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
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AuthException(AuthErrorCode.INVALID_PASSWORD);
        }

        // 기존 RefreshToken 삭제
        refreshTokenRedisRepository.deleteByUserId(user.getId());

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
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        refreshTokenRedisRepository.deleteByUserId(user.getId());
    }

    // RefreshToken 저장 공통 메서드
    private void saveRefreshToken(User user, String refreshToken) {
        String hashedToken = hashToken(refreshToken);
        refreshTokenRedisRepository.save(user.getId(), hashedToken);
    }

    // SHA-256 해싱 메서드
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("토큰 해싱 실패", e);
        }
    }

    // 토큰 갱신
    @Transactional
    public TokenRefreshResDTO refresh(TokenRefreshReqDTO request) {

        String refreshToken = request.getRefreshToken();

        // Refresh Token 유효성 검증
        if (!jwtUtil.isTokenValid(refreshToken)) {
            throw new AuthException(AuthErrorCode.INVALID_TOKEN);
        }

        // Refresh Token 타입 확인
        if (!jwtUtil.isRefreshToken(refreshToken)) {
            throw new AuthException(AuthErrorCode.NOT_REFRESH_TOKEN);
        }

        // SHA-256 해싱 후 Redis 조회
        String hashedToken = hashToken(refreshToken);
        String userId = refreshTokenRedisRepository.findUserIdByTokenHash(hashedToken)
                .orElseThrow(() -> new AuthException(AuthErrorCode.TOKEN_NOT_FOUND));

        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        // 새 토큰 발급
        String newAccessToken = jwtUtil.generateAccessToken(user.getEmail());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        // 원자적 토큰 회전
        String newHashedToken = hashToken(newRefreshToken);
        refreshTokenRedisRepository.rotateToken(user.getId(), hashedToken, newHashedToken);

        return new TokenRefreshResDTO(newAccessToken, newRefreshToken);
    }

    // 비밀번호 변경
    @Transactional
    public void changePassword(String email, PasswordChangeReqDTO request) {

        // 유저 조회
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new AuthException(AuthErrorCode.INVALID_PASSWORD);
        }

        // 새 비밀번호 확인
        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new AuthException(AuthErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        // 현재 비밀번호랑 새 비밀번호가 같으면 에러
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new AuthException(AuthErrorCode.SAME_AS_OLD_PASSWORD);
        }

        // 비밀번호 변경
        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));

        // 기존 Refresh Token 전체 삭제 (재로그인 유도)
        refreshTokenRedisRepository.deleteByUserId(user.getId());
    }

    // 비밀번호 찾기 - 이메일 인증 발송
    @Transactional
    public void sendPasswordResetEmail(PasswordResetEmailReqDTO request) {

        // 이름 + 이메일로 유저 확인
        User user = userRepository.findByEmail(request.getEmail())
                .filter(u -> u.getUsername().equals(request.getUsername()))
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        String code = emailService.generateCode();

        int sendCount = emailVerificationRedisRepository.increaseSendCountAtomic(
                EmailVerificationType.PASSWORD_RESET, request.getEmail(), code);
        if (sendCount == -1) {
            throw new AuthException(AuthErrorCode.EMAIL_CODE_EXCEED_SEND);
        }

        eventPublisher.publishEvent(
                new EmailVerificationEvent(request.getEmail(), code, EmailVerificationType.PASSWORD_RESET));
    }

    // 비밀번호 찾기 - 인증번호 확인
    @Transactional
    public void verifyPasswordResetCode(PasswordResetVerifyReqDTO request) {

        if (!emailVerificationRedisRepository.exists(EmailVerificationType.PASSWORD_RESET, request.getEmail())) {
            throw new AuthException(AuthErrorCode.EMAIL_CODE_NOT_FOUND);
        }

        if (emailVerificationRedisRepository.getFailCount(EmailVerificationType.PASSWORD_RESET, request.getEmail()) >= 5) {
            throw new AuthException(AuthErrorCode.EMAIL_CODE_EXCEED_FAIL);
        }

        String storedCode = emailVerificationRedisRepository.findCode(EmailVerificationType.PASSWORD_RESET, request.getEmail());

        // null 체크 추가
        if (storedCode == null) {
            throw new AuthException(AuthErrorCode.EMAIL_CODE_NOT_FOUND);
        }

        if (!storedCode.equals(request.getCode())) {
            emailVerificationService.increaseFailCount(EmailVerificationType.PASSWORD_RESET, request.getEmail());
            throw new AuthException(AuthErrorCode.EMAIL_CODE_MISMATCH);
        }

        emailVerificationRedisRepository.verify(EmailVerificationType.PASSWORD_RESET, request.getEmail());
    }

    // 비밀번호 재설정
    @Transactional
    public void resetPassword(PasswordResetConfirmReqDTO request) {

        if (!emailVerificationRedisRepository.exists(EmailVerificationType.PASSWORD_RESET, request.getEmail())) {
            throw new AuthException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (!emailVerificationRedisRepository.isVerified(EmailVerificationType.PASSWORD_RESET, request.getEmail())) {
            throw new AuthException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new AuthException(AuthErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));

        refreshTokenRedisRepository.deleteByUserId(user.getId());
        emailVerificationRedisRepository.delete(EmailVerificationType.PASSWORD_RESET, request.getEmail());
    }

    // 부모님 이메일 인증 코드 발송
    @Transactional
    public void sendParentVerifyEmail(ParentEmailSendReqDTO request) {

        String code = emailService.generateCode();

        int sendCount = emailVerificationRedisRepository.increaseSendCountAtomic(
                EmailVerificationType.PARENT_VERIFY, request.getParentEmail(), code);
        if (sendCount == -1) {
            throw new AuthException(AuthErrorCode.EMAIL_CODE_EXCEED_SEND);
        }

        eventPublisher.publishEvent(
                new EmailVerificationEvent(request.getParentEmail(), code, EmailVerificationType.PARENT_VERIFY));
    }

    // 부모님 이메일 인증 확인
    @Transactional
    public void verifyParentEmail(String email, ParentEmailVerifyReqDTO request) {

        if (!emailVerificationRedisRepository.exists(EmailVerificationType.PARENT_VERIFY, request.getParentEmail())) {
            throw new AuthException(AuthErrorCode.EMAIL_CODE_NOT_FOUND);
        }

        if (emailVerificationRedisRepository.getFailCount(EmailVerificationType.PARENT_VERIFY, request.getParentEmail()) >= 5) {
            throw new AuthException(AuthErrorCode.EMAIL_CODE_EXCEED_FAIL);
        }

        String storedCode = emailVerificationRedisRepository.findCode(EmailVerificationType.PARENT_VERIFY, request.getParentEmail());

        // null 체크 추가
        if (storedCode == null) {
            throw new AuthException(AuthErrorCode.EMAIL_CODE_NOT_FOUND);
        }

        if (!storedCode.equals(request.getCode())) {
            emailVerificationService.increaseFailCount(EmailVerificationType.PARENT_VERIFY, request.getParentEmail());
            throw new AuthException(AuthErrorCode.EMAIL_CODE_MISMATCH);
        }

        emailVerificationRedisRepository.verify(EmailVerificationType.PARENT_VERIFY, request.getParentEmail());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        user.updateParentInfo(request.getParentEmail());
    }

    // 생년월일 저장
    @Transactional
    public BirthDateResDTO saveBirthDate(String email, BirthDateReqDTO request) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        // 생년월일 저장
        user.updateBirthDate(request.getBirthDate());

        // 만 14세 미만 여부 계산
        int age = Period.between(request.getBirthDate(), LocalDate.now()).getYears();
        boolean isUnder14 = age < 14;

        return new BirthDateResDTO(isUnder14);
    }

    // 키워드 개수 설정
    @Transactional
    public void saveKeywordCount(String email, KeywordCountReqDTO request) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        user.updatePreferredKeywordCount(request.getPreferredKeywordCount());
    }
}