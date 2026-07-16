package com.solux31.nubee_BE.domain.auth.repository;

import com.solux31.nubee_BE.domain.auth.entity.EmailVerification;
import com.solux31.nubee_BE.domain.auth.enums.EmailVerificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    // 이메일 + 타입으로 최신 인증 코드 조회
    Optional<EmailVerification> findTopByEmailAndTypeOrderByCreatedAtDesc(String email, EmailVerificationType type);

    // 인증 완료된 코드 조회
    Optional<EmailVerification> findTopByEmailAndTypeAndIsVerifiedTrueOrderByCreatedAtDesc(String email, EmailVerificationType type);

    // 기존 인증 코드 삭제
    void deleteByEmailAndType(String email, EmailVerificationType type);
}