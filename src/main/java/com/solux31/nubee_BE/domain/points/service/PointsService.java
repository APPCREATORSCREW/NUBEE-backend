package com.solux31.nubee_BE.domain.points.service;

import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.exception.code.AuthErrorCode;
import com.solux31.nubee_BE.domain.auth.exception.AuthException;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.domain.points.converter.PointsConverter;
import com.solux31.nubee_BE.domain.points.dto.PointsResDTO;
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

@Service
@RequiredArgsConstructor
public class PointsService {
    private static final int LEVEL_UP_THRESHOLD = 50;
    private static final int SKIN_GRANT_LEVEL_UNIT = 5;

    private final UserRepository userRepository;
    private final PointsRepository pointsRepository;
    private final SkinRepository skinRepository;
    private final UserSkinRepository userSkinRepository;
    private final PointsConverter pointsConverter;

    public PointsResDTO.PointInfo getPoints(Long userId) {
        User user = getUserOrThrow(userId);
        return pointsConverter.toPointInfo(user);
    }

    @Transactional
    public PointsResDTO.PointResult addPoint(Long userId, int amount, String reason) {
        if (amount < 0) {
            throw new PointsException(PointsErrorCode.INVALID_AMOUNT);
        }
        if (reason == null || reason.isBlank() || reason.codePointCount(0, reason.length()) > 50) {
            throw new PointsException(PointsErrorCode.INVALID_REASON);
        }

        User user = getUserOrThrow(userId);

        //적립 내역 기록
        pointsRepository.save(PointHistory.builder()
                .user(user)
                .amount(amount)
                .reason(reason)
                .build());

        //포인트 적립
        user.updatePoint(amount);

        boolean leveledUp = false;
        Skin newSkin = null;

        //50포인트마다 레벨업
        while (user.getPoint() >= LEVEL_UP_THRESHOLD) {
            user.updatePoint(-LEVEL_UP_THRESHOLD); // 50 차감 (초과분은 이월)
            int newLevel = user.getCurrentLevel() + 1;
            user.updateLevel(newLevel);
            leveledUp = true;

            // 5레벨마다 스킨 지급
            if (newLevel % SKIN_GRANT_LEVEL_UNIT == 0) {
                newSkin = grantSkinForLevel(user, newLevel);
            }
        }

        return PointsResDTO.PointResult.builder()
                .earnedPoint(amount)
                .currentPoint(user.getPoint())
                .leveledUp(leveledUp)
                .currentLevel(user.getCurrentLevel())
                .newSkin(pointsConverter.toNewSkinInfo(newSkin))
                .build();
    }

    // 레벨에 맞는 스킨 지급 (이미 보유중이면 스킵)
    private Skin grantSkinForLevel(User user, int level) {
        Skin skin = skinRepository.findByRequiredLevel(level)
                .orElse(null);

        if (skin == null) {
            return null; // 해당 레벨에 지급할 스킨이 카탈로그에 없으면 무시
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

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
    }
}
