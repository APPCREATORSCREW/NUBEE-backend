package com.solux31.nubee_BE.domain.points.service;

import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.exception.AuthException;
import com.solux31.nubee_BE.domain.auth.exception.code.AuthErrorCode;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.domain.points.dto.Response.NewSkinInfoResDTO;
import com.solux31.nubee_BE.domain.points.dto.Response.PointResultResDTO;
import com.solux31.nubee_BE.domain.points.entity.PointHistory;
import com.solux31.nubee_BE.domain.points.exception.PointsException;
import com.solux31.nubee_BE.domain.points.exception.code.PointsErrorCode;
import com.solux31.nubee_BE.domain.points.repository.PointsRepository;
import com.solux31.nubee_BE.domain.profile.entity.Skin;
import com.solux31.nubee_BE.domain.profile.entity.mapping.UserSkin;
import com.solux31.nubee_BE.domain.profile.repository.SkinRepository;
import com.solux31.nubee_BE.domain.profile.repository.UserSkinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PointsService {
    private static final int LEVEL_UP_THRESHOLD = 50;
    private static final int SKIN_GRANT_LEVEL_UNIT = 5;

    private final UserRepository userRepository;
    private final PointsRepository pointsRepository;
    private final SkinRepository skinRepository;
    private final UserSkinRepository userSkinRepository;

    @Transactional
    public PointResultResDTO addPoint(Long userId, int amount, String reason) {
        if (amount < 0) {
            throw new PointsException(PointsErrorCode.INVALID_AMOUNT);
        }
        if (reason == null || reason.isBlank() || reason.codePointCount(0, reason.length()) > 50) {
            throw new PointsException(PointsErrorCode.INVALID_REASON);
        }

        User user = getUserOrThrow(userId);

        pointsRepository.save(PointHistory.builder()
                .user(user)
                .amount(amount)
                .reason(reason)
                .build());

        user.updatePoint(amount);

        boolean leveledUp = false;
        List<Skin> newSkins = new ArrayList<>();

        while (user.getPoint() >= LEVEL_UP_THRESHOLD) {
            user.updatePoint(-LEVEL_UP_THRESHOLD);
            int newLevel = user.getCurrentLevel() + 1;
            user.updateLevel(newLevel);
            leveledUp = true;

            if (newLevel % SKIN_GRANT_LEVEL_UNIT == 0) {
                Skin granted = grantSkinForLevel(user, newLevel);
                if (granted != null) {
                    newSkins.add(granted);
                }
            }
        }

        return PointResultResDTO.builder()
                .earnedPoint(amount)
                .currentPoint(user.getPoint())
                .leveledUp(leveledUp)
                .currentLevel(user.getCurrentLevel())
                .newSkins(newSkins.stream()
                        .map(this::toNewSkinInfo)
                        .toList())
                .build();
    }

    private Skin grantSkinForLevel(User user, int level) {
        Skin skin = skinRepository.findByRequiredLevel(level)
                .orElse(null);

        if (skin == null) {
            return null;
        }

        boolean alreadyOwned = userSkinRepository.existsByUserIdAndSkinId(user.getId(), skin.getId());

        if (alreadyOwned) {
            return null;
        }

        userSkinRepository.save(UserSkin.builder()
                .user(user)
                .skin(skin)
                .acquiredAt(LocalDateTime.now())
                .build());

        return skin;
    }

    private NewSkinInfoResDTO toNewSkinInfo(Skin skin) {
        return NewSkinInfoResDTO.builder()
                .skinId(skin.getId())
                .skinName(skin.getSkinName())
                .build();
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
    }
}