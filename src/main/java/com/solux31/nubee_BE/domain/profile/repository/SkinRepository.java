package com.solux31.nubee_BE.domain.profile.repository;

import com.solux31.nubee_BE.domain.profile.entity.Skin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkinRepository extends JpaRepository<Skin, Long> {
    Optional<Skin> findByRequiredLevel(int requiredLevel);
    Optional<Skin> findBySkinCode(String skinCode);  // 추가
}