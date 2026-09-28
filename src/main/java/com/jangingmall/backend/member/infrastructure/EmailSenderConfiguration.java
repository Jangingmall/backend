package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class EmailSenderConfiguration {

    @Bean
    @ConditionalOnBean(JavaMailSender.class)
    public EmailSender springMailEmailSender(JavaMailSender mailSender,
        @Value("${spring.mail.from:${spring.mail.username:noreply@midam.store}}") String from) {
        return new EmailSender() {
            @Override
            public void sendVerificationCode(String toEmail, String code) {
                send(toEmail, "[미담] 이메일 인증 코드", "인증 코드: " + code + "\n\n코드는 5분간 유효합니다.");
            }

            @Override
            public void sendVerificationLink(String toEmail, String verificationUrl) {
                send(toEmail, "[장인몰] 이메일 인증을 완료해주세요",
                    "아래 링크를 눌러 이메일 인증을 완료해주세요.\n\n" + verificationUrl);
            }

            private void send(String toEmail, String subject, String text) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(toEmail);
                message.setSubject(subject);
                message.setText(text);
                mailSender.send(message);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender noOpEmailSender() {
        Logger log = LoggerFactory.getLogger(EmailSenderConfiguration.class);
        return (toEmail, code) -> log.warn("[메일 미설정] 인증 메일 발송 생략: {}", toEmail);
    }
}
