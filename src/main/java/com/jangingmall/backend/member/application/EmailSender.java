package com.jangingmall.backend.member.application;

public interface EmailSender {
    void sendVerificationCode(String toEmail, String code);
}
