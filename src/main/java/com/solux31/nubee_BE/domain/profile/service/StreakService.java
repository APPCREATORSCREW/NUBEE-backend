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
        LocalDate today = LocalDate.now();

        UserStreak latest = userStreakRepository.findTopByUserIdOrderByAchievedDateDesc(user.getId())
                .orElse(null);

        if (latest != null && latest.getAchievedDate().equals(today)) {
            // 오늘 이미 기록 있음 → 중복 호출, 아무것도 안 함
            return;
        }

        int newStreak;
        if (latest != null && latest.getAchievedDate().equals(today.minusDays(1))) {
            // 어제 기록 있음 → 연속 유지, +1
            newStreak = latest.getCurrentStreak() + 1;
        } else {
            // 어제 기록 없음(며칠 건너뜀 또는 최초) → 스트릭 리셋
            newStreak = 1;
        }

        userStreakRepository.save(UserStreak.builder()
                .user(user)
                .achievedDate(today)
                .currentStreak(newStreak)
                .build());

        if (newStreak % STREAK_BONUS_UNIT == 0) {
            pointsService.addPoint(user.getId(), 1, "연속학습 " + newStreak + "일 달성");
        }
    }
}
