package com.solux31.nubee_BE.domain.profile.repository;

import com.solux31.nubee_BE.domain.profile.entity.UserStreak;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserStreakRepository extends JpaRepository<UserStreak, Long> {
    //가장 최근 스트릭 기록 하나만 가져오는 메서드
    Optional<UserStreak> findTopByUserIdOrderByAchievedDateDesc(Long userId);
}
