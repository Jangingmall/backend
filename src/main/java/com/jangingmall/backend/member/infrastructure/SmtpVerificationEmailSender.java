package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.application.EmailVerificationProperties;
import com.jangingmall.backend.member.application.VerificationEmailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class SmtpVerificationEmailSender implements VerificationEmailSender {

    private final JavaMailSender mailSender;
    private final EmailVerificationProperties properties;

    public SmtpVerificationEmailSender(
        JavaMailSender mailSender,
        EmailVerificationProperties properties
    ) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String recipientEmail, String token) {
        String verificationUrl = UriComponentsBuilder.fromUri(properties.verificationUrl())
            .queryParam("token", token)
            .build()
            .encode()
            .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(recipientEmail);
        message.setSubject("[장인몰] 이메일 인증을 완료해주세요");
        message.setText("아래 링크를 눌러 이메일 인증을 완료해주세요.\n\n" + verificationUrl);
        mailSender.send(message);
    }
}
