package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class EmailSenderConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderConfiguration.class);

    @Bean
    @Profile("prod")
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender productionEmailSender(ObjectProvider<JavaMailSender> mailSenderProvider,
        @Value("${member.email-verification.from:${spring.mail.username:noreply@midam.store}}") String from) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return (toEmail, code) -> {
                throw new IllegalStateException("SMTP is not configured; refusing to skip email verification.");
            };
        }
        return smtpEmailSender(mailSender, from);
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    @Profile({"local", "test", "local-postgresql"})
    public EmailSender noOpEmailSender() {
        return (toEmail, code) -> log.warn("[메일 미설정] 인증 코드 발송 생략");
    }

    private EmailSender smtpEmailSender(JavaMailSender mailSender, String from) {
        return (toEmail, code) -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject("[미담] 이메일 인증 코드");
            message.setText("인증 코드: " + code + "\n\n코드는 5분간 유효합니다.");
            mailSender.send(message);
        };
    }
}
