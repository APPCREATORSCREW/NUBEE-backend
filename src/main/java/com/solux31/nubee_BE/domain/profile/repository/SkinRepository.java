package com.solux31.nubee_BE.domain.profile.repository;

import com.solux31.nubee_BE.domain.profile.entity.Skin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SkinRepository extends JpaRepository<Skin, Long> {
    Optional<Skin> findByRequiredLevel(int requiredLevel);
}
