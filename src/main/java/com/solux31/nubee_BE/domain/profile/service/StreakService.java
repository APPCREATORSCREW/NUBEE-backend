package com.solux31.nubee_BE.domain.profile.service;

import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.points.service.PointsService;
import com.solux31.nubee_BE.domain.profile.entity.UserStreak;
import com.solux31.nubee_BE.domain.profile.repository.UserStreakRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class StreakService {
    private static final int STREAK_BONUS_UNIT = 5;

    private final UserStreakRepository userStreakRepository;
    private final PointsService pointsService;

    @Transactional
    public void updateStreak(User user) {
        int lastestStreak = userStreakRepository.findTopByUserIdOrderByAchievedDateDesc(user.getId())
                .map(UserStreak::getCurrentStreak)
                .orElse(0);

        int newStreak = lastestStreak + 1;

        userStreakRepository.save(UserStreak.builder()
                .user(user)
                .achievedDate(LocalDate.now())
                .currentStreak(newStreak)
                .build());

        if (newStreak % STREAK_BONUS_UNIT == 0) {
            pointsService.addPoint(user.getId(), 1, "연속학습 " + newStreak + "일 달성");
        }
    }
}
