package com.jangingmall.backend.member.application;

public interface VerificationEmailSender {

    void send(String recipientEmail, String token);
}
