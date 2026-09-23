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
        @Value("${spring.mail.username:noreply@midam.store}") String from) {
        return (toEmail, code) -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("[미담] 이메일 인증 코드");
            message.setText("인증 코드: " + code + "\n\n코드는 5분간 유효합니다.");
            mailSender.send(message);
        };
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender noOpEmailSender() {
        Logger log = LoggerFactory.getLogger(EmailSenderConfiguration.class);
        return (toEmail, code) -> log.warn("[메일 미설정] 인증 코드 {} → {} 발송 생략", code, toEmail);
    }
}
