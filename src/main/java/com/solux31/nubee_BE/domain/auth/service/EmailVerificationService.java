package com.solux31.nubee_BE.domain.auth.service;

import com.solux31.nubee_BE.domain.auth.enums.EmailVerificationType;
import com.solux31.nubee_BE.domain.auth.repository.EmailVerificationRedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationRedisRepository emailVerificationRedisRepository;

    public void increaseFailCount(EmailVerificationType type, String email) {
        emailVerificationRedisRepository.increaseFailCount(type, email);
    }
}