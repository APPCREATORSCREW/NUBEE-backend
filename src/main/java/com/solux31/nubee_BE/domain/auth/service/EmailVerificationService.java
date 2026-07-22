package com.solux31.nubee_BE.domain.auth.service;

import com.solux31.nubee_BE.domain.auth.entity.EmailVerification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    // 별도 트랜잭션으로 failCount 증가 (롤백 방지)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increaseFailCount(EmailVerification verification) {
        verification.increaseFailCount();
    }
}