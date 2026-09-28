package com.jangingmall.backend.member.application;

public interface EmailSender {
    void sendVerificationCode(String toEmail, String code);

    default void sendVerificationLink(String toEmail, String verificationUrl) {
        sendVerificationCode(toEmail, verificationUrl);
    }
}
