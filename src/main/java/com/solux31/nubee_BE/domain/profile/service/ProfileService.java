package com.solux31.nubee_BE.domain.profile.service;

import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.exception.AuthException;
import com.solux31.nubee_BE.domain.auth.exception.code.AuthErrorCode;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.domain.profile.dto.Request.PresignedUrlReqDTO;
import com.solux31.nubee_BE.domain.profile.dto.Request.ProfileImageUpdateReqDTO;
import com.solux31.nubee_BE.domain.profile.dto.Request.SettingUpdateReqDTO;
import com.solux31.nubee_BE.domain.profile.dto.Request.SkinApplyReqDTO;
import com.solux31.nubee_BE.domain.profile.dto.Response.*;
import com.solux31.nubee_BE.domain.profile.entity.Skin;
import com.solux31.nubee_BE.domain.profile.entity.mapping.UserSkin;
import com.solux31.nubee_BE.domain.profile.entity.UserStreak;
import com.solux31.nubee_BE.domain.profile.exception.ProfileException;
import com.solux31.nubee_BE.domain.profile.exception.code.ProfileErrorCode;
import com.solux31.nubee_BE.domain.profile.repository.SkinRepository;
import com.solux31.nubee_BE.domain.profile.repository.UserSkinRepository;
import com.solux31.nubee_BE.domain.profile.repository.UserStreakRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final UserRepository userRepository;
    private final UserSkinRepository userSkinRepository;
    private final SkinRepository skinRepository;
    private final UserStreakRepository userStreakRepository;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    public ProfileResDTO getProfile(Long userId) {
        User user = getUserOrThrow(userId);

        int currentStreak = userStreakRepository.findTopByUserIdOrderByAchievedDateDesc(userId)
                .map(UserStreak::getCurrentStreak)
                .orElse(0);

        List<Skin> allSkins = skinRepository.findAll();
        List<UserSkin> ownedSkins = userSkinRepository.findAllByUserId(userId);
        List<SkinInfoResDTO> skinInfos = toSkinInfoList(allSkins, ownedSkins);

        UserSkin currentUserSkin = user.getCurrentSkin();
        if (currentUserSkin == null) {
            throw new ProfileException(ProfileErrorCode.SKIN_NOT_FOUND);
        }
        Skin currentSkin = currentUserSkin.getSkin();

        return ProfileResDTO.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())
                .currentLevel(user.getCurrentLevel())
                .currentPoint(user.getPoint())
                .currentStreak(currentStreak)
                .currentSkinId(currentSkin.getId())
                .currentSkinName(currentSkin.getSkinName())
                .skins(skinInfos)
                .build();
    }

    public SettingsResDTO getSettings(Long userId) {
        User user = getUserOrThrow(userId);

        return SettingsResDTO.builder()
                .preferredKeywordCount(user.getPreferredKeywordCount())
                .notificationEnabled(user.isNotificationEnabled())
                .notificationTime(user.getNotificationTime() != null
                        ? user.getNotificationTime().toString() : null)
                .build();
    }

    @Transactional
    public SettingsResDTO updateSettings(Long userId, SettingUpdateReqDTO request) {
        User user = getUserOrThrow(userId);

        if (request.getPreferredKeywordCount() != null) {
            user.updatePreferredKeywordCount(request.getPreferredKeywordCount());
        }

        boolean enabled = request.getNotificationEnabled() != null
                ? request.getNotificationEnabled()
                : user.isNotificationEnabled();   // 안 왔으면 기존 값 유지

        LocalTime time = (request.getNotificationTime() != null && !request.getNotificationTime().isBlank())
                ? LocalTime.parse(request.getNotificationTime())
                : user.getNotificationTime();      // 안 왔으면 기존 값 유지

        user.updateNotificationSettings(enabled, time);

        return SettingsResDTO.builder()
                .preferredKeywordCount(user.getPreferredKeywordCount())
                .notificationEnabled(user.isNotificationEnabled())
                .notificationTime(user.getNotificationTime() != null
                        ? user.getNotificationTime().toString() : null)
                .build();
    }

    @Transactional
    public SkinApplyResDTO applySkin(Long userId, SkinApplyReqDTO request) {
        User user = getUserOrThrow(userId);

        UserSkin targetUserSkin = userSkinRepository.findAllByUserId(userId).stream()
                .filter(us -> us.getSkin().getId().equals(request.getSkinId()))
                .findFirst()
                .orElseThrow(() -> new ProfileException(ProfileErrorCode.SKIN_NOT_OWNED));

        user.updateCurrentSkin(targetUserSkin);

        return SkinApplyResDTO.builder()
                .currentSkinId(targetUserSkin.getSkin().getId())
                .currentSkinName(targetUserSkin.getSkin().getSkinName())
                .build();
    }

    @Transactional
    public ProfileImageResDTO updateProfileImage(Long userId, ProfileImageUpdateReqDTO request) {
        User user = getUserOrThrow(userId);
        user.updateProfileImage(request.getProfileImageUrl());

        return ProfileImageResDTO.builder()
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }

    private SkinInfoResDTO toSkinInfo(Skin skin, boolean isOwned) {
        return SkinInfoResDTO.builder()
                .skinId(skin.getId())
                .skinName(skin.getSkinName())
                .imageUrl(skin.getImageUrl())
                .isOwned(isOwned)
                .build();
    }

    private List<SkinInfoResDTO> toSkinInfoList(List<Skin> allSkins, List<UserSkin> ownedSkins) {
        Set<Long> ownedSkinIds = ownedSkins.stream()
                .map(us -> us.getSkin().getId())
                .collect(Collectors.toSet());

        return allSkins.stream()
                .map(skin -> toSkinInfo(skin, ownedSkinIds.contains(skin.getId())))
                .toList();
    }

    public PresignedUrlResDTO createPresignedUrl(PresignedUrlReqDTO request) {
        String originalFileName = request.getFileName();

        // 1. 파일명 길이 검증 (S3 Object Key 최대 1,024바이트 제한)
        if (originalFileName == null || originalFileName.getBytes(StandardCharsets.UTF_8).length > 900) {
            throw new ProfileException(ProfileErrorCode.INVALID_FILE_NAME); // 적절한 Exception 지정
        }

        // 2. 확장자 추출 및 검증 (없거나 이상한 문자 방지)
        String extension = StringUtils.getFilenameExtension(originalFileName);
        if (extension == null || extension.isBlank()) {
            throw new ProfileException(ProfileErrorCode.INVALID_FILE_EXTENSION);
        }

        // 이미지 확장자 목록 정의
        List<String> allowedExtensions = List.of("png", "jpg", "jpeg", "webp", "gif");

        // 허용되지 않은 확장자인 경우 예외 발생
        if (!allowedExtensions.contains(extension.toLowerCase())) {
            throw new ProfileException(ProfileErrorCode.INVALID_FILE_EXTENSION);
        }

        // 3. Raw Key 생성 (원본 파일명 대신 안전하게 UUID + 확장자만 사용)
        // 예: profile/550e8400-e29b-41d4-a716-446655440000.png
        String key = "profile/" + UUID.randomUUID() + "." + extension.toLowerCase();

        // 4. Presigned URL 생성
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        // 5. 안전한 S3 Public URL 생성 (Key 부분 인코딩)
        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8)
                .replace("+", "%20")  // URLEncoder가 공백을 '+'로 바꿀 수 있어 %20으로 치환
                .replace("%2F", "/"); // 경로 구분자 '/'는 인코딩에서 제외

        String region = "ap-northeast-2"; // 또는 @Value("${aws.region}")으로 들어온 변수
        String fileUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", bucket, region, encodedKey);

        return PresignedUrlResDTO.builder()
                .uploadUrl(presignedRequest.url().toString())
                .fileUrl(fileUrl)
                .build();
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
    }
}