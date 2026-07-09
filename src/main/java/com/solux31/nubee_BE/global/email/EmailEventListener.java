package com.solux31.nubee_BE.global.email;

import com.solux31.nubee_BE.domain.auth.enums.EmailVerificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EmailEventListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleEmailVerificationEvent(EmailVerificationEvent event) {
        if (event.getType() == EmailVerificationType.PASSWORD_RESET) {
            emailService.sendPasswordResetEmail(event.getEmail(), event.getCode());
        } else if (event.getType() == EmailVerificationType.PARENT_VERIFY) {
            emailService.sendParentVerifyEmail(event.getEmail(), event.getCode());
        }
    }
}