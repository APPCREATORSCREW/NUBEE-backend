package com.solux31.nubee_BE.domain.profile.service;

import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.domain.profile.dto.ProfileReqDTO;
import com.solux31.nubee_BE.domain.profile.dto.ProfileResDTO;
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
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final UserRepository userRepository;
    private final UserSkinRepository userSkinRepository;
    private final SkinRepository skinRepository;
    private final UserStreakRepository userStreakRepository;

    public ProfileResDTO.Profile getProfile(Long userId) {
        User user = getUserOrThrow(userId);

        int currentStreak = userStreakRepository.findTopByUserIdOrderByAchievedDateDesc(userId)
                .map(UserStreak::getCurrentStreak)
                .orElse(0);

        List<Skin> allSkins = skinRepository.findAll();
        List<UserSkin> ownedSkins = userSkinRepository.findAllByUserId(userId);
        List<ProfileResDTO.SkinInfo> skinInfos = toSkinInfoList(allSkins, ownedSkins);

        // user.getCurrentSkin()이 이미 UserSkin 객체이므로, 거기서 Skin을 꺼냄
        UserSkin currentUserSkin = user.getCurrentSkin();
        if (currentUserSkin == null) {
            throw new ProfileException(ProfileErrorCode.SKIN_NOT_FOUND);
        }
        Skin currentSkin = currentUserSkin.getSkin();  // UserSkin 안의 Skin 필드 사용

        return ProfileResDTO.Profile.builder()
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

    public ProfileResDTO.Settings getSettings(Long userId) {
        User user = getUserOrThrow(userId);

        return ProfileResDTO.Settings.builder()
                .preferredKeywordCount(user.getPreferredKeywordCount())
                .notificationEnabled(user.isNotificationEnabled())
                .notificationTime(user.getNotificationTime() != null
                    ? user.getNotificationTime().toString() : null)
                .build();
    }

    @Transactional
    public ProfileResDTO.Settings updateSettings(Long userId, ProfileReqDTO.SettingUpdate request) {
        User user = getUserOrThrow(userId);

        user.updatePreferredKeywordCount(request.getPreferredKeywordCount());

        LocalTime time = (request.getNotificationTime() != null && !request.getNotificationTime().isBlank())
                ? LocalTime.parse(request.getNotificationTime())
                : null;

        user.updateNotificationSettings(request.isNotificationEnabled(), time);

        return ProfileResDTO.Settings.builder()
                .preferredKeywordCount(user.getPreferredKeywordCount())
                .notificationEnabled(user.isNotificationEnabled())
                .notificationTime(user.getNotificationTime() != null
                    ? user.getNotificationTime().toString() : null)
                .build();
    }

    @Transactional
    public ProfileResDTO.SkinApply applySkin(Long userId, ProfileReqDTO.SkinApply request) {
        User user = getUserOrThrow(userId);

        UserSkin targetUserSkin = userSkinRepository.findAllByUserId(userId).stream()
                .filter(us -> us.getSkin().getId().equals(request.getSkinId()))
                .findFirst()
                .orElseThrow(() -> new ProfileException(ProfileErrorCode.SKIN_NOT_OWNED));

        user.updateCurrentSkin(targetUserSkin);

        return ProfileResDTO.SkinApply.builder()
                .currentSkinId(targetUserSkin.getSkin().getId())
                .currentSkinName(targetUserSkin.getSkin().getSkinName())
                .build();
    }

    @Transactional
    public ProfileResDTO.ProfileImage updateProfileImage(Long userId, ProfileReqDTO.ProfileImageUpdate request) {
        User user = getUserOrThrow(userId);
        user.updateProfileImage(request.getProfileImageUrl());

        return ProfileResDTO.ProfileImage.builder()
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }

    private ProfileResDTO.SkinInfo toSkinInfo(Skin skin, boolean isOwned) {
        return ProfileResDTO.SkinInfo.builder()
                .skinId(skin.getId())
                .skinName(skin.getSkinName())
                .imageUrl(skin.getImageUrl())
                .isOwned(isOwned)
                .build();
    }

    private List<ProfileResDTO.SkinInfo> toSkinInfoList(List<Skin> allSkins, List<UserSkin> ownedSkins) {
        Set<Long> ownedSkinIds = ownedSkins.stream()
                .map(us -> us.getSkin().getId())
                .collect(Collectors.toSet());

        return allSkins.stream()
                .map(skin -> toSkinInfo(skin, ownedSkinIds.contains(skin.getId())))
                .toList();
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
    }
}
