package com.solux31.nubee_BE.domain.auth.repository;

import com.solux31.nubee_BE.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    Optional<User> findByKakaoId(String kakaoId);
}