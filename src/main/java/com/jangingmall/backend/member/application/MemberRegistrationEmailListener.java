package com.jangingmall.backend.member.application;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MemberRegistrationEmailListener {

    private static final Logger log = LoggerFactory.getLogger(MemberRegistrationEmailListener.class);

    private final EmailVerificationService emailVerificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendVerificationEmail(MemberRegisteredEvent event) {
        try {
            emailVerificationService.sendVerification(event.memberId(), event.email());
        } catch (RuntimeException exception) {
            log.error("Failed to send signup verification email for member {}", event.memberId(), exception);
        }
    }
}
