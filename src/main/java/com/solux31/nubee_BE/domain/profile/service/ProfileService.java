package com.solux31.nubee_BE.domain.profile.service;

import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.domain.profile.converter.ProfileConverter;
import com.solux31.nubee_BE.domain.profile.dto.ProfileReqDTO;
import com.solux31.nubee_BE.domain.profile.dto.ProfileResDTO;
import com.solux31.nubee_BE.domain.profile.entity.Skin;
import com.solux31.nubee_BE.domain.profile.entity.UserSkin;
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

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final UserRepository userRepository;
    private final UserSkinRepository userSkinRepository;
    private final SkinRepository skinRepository;
    private final UserStreakRepository userStreakRepository;
    private final ProfileConverter profileConverter;

    public ProfileResDTO.Profile getProfile(Long userId) {
        User user = getUserOrThrow(userId);

        int currentStreak = userStreakRepository.findTopByUserIdOrderByAchievedDateDesc(userId)
                .map(UserStreak::getCurrentStreak)
                .orElse(0);

        List<Skin> allSkins = skinRepository.findAll();
        List<UserSkin> ownedSkins = userSkinRepository.findAllByUserId(userId);
        List<ProfileResDTO.SkinInfo> skinInfos = profileConverter.toSkinInfoList(allSkins, ownedSkins);

        Skin currentSkin = skinRepository.findById(user.getCurrentSkinId())
                .orElseThrow(() -> new ProfileException(ProfileErrorCode.SKIN_NOT_FOUND));

        return ProfileResDTO.Profile.builder()
                .username(user.getUsername())
                .email(user.getEmail())
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
        user.updateNotificationSettings(
                request.isNotificationEnabled(),
                LocalTime.parse(request.getNotificationTime())
        );

        return ProfileResDTO.Settings.builder()
                .preferredKeywordCount(user.getPreferredKeywordCount())
                .notificationEnabled(user.isNotificationEnabled())
                .notificationTime(user.getNotificationTime().toString())
                .build();
    }

    @Transactional
    public ProfileResDTO.SkinApply applySkin(Long userId, ProfileReqDTO.SkinApply request) {
        User user = getUserOrThrow(userId);

        boolean isOwned = userSkinRepository.findAllByUserId(userId).stream()
                .anyMatch(us -> us.getSkin().getId().equals(request.getSkinId()));

        if (!isOwned) {
            throw new ProfileException(ProfileErrorCode.SKIN_NOT_OWNED);
        }

        Skin skin = skinRepository.findById(request.getSkinId())
                .orElseThrow(() -> new ProfileException(ProfileErrorCode.SKIN_NOT_FOUND));

        user.updateCurrentSkin(skin.getId());

        return ProfileResDTO.SkinApply.builder()
                .currentSkinId(skin.getId())
                .currentSkinName(skin.getSkinName())
                .build();
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
    }
}
