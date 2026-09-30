package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.global.config.EmailRealConfig;
import com.jangingmall.backend.global.config.EmailRequest;
import com.jangingmall.backend.global.exception.BusinessException;
import com.jangingmall.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Slf4j
@Configuration
public class EmailSenderService {
    private final JavaMailSender emailConfig;
    private final String email;

    public EmailSenderService(EmailRealConfig emailConfig,
                              @Value("${spring.mail.username}") String email) {
        this.emailConfig = emailConfig.getJavaMailSender();
        this.email = email;
    }


    public void signupCertSend(String email, String code) {
        try {
            String title = "[미담] 이메일 인증 코드";
            String sendText = """
                      인증 코드: %s
                      코드는 5분간 유효합니다.
                    """.formatted(code);
            sendMessage(email, title, sendText);
        } catch (MailParseException e1) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_PARSE);
        } catch (MailAuthenticationException e2) {
            System.out.println(e2.fillInStackTrace());
            throw new BusinessException(ErrorCode.EMAIL_SEND_AUTHENTICATION);
        } catch (MailSendException e3) {
            throw new BusinessException(ErrorCode.EMAIL_SEND);
        }
    }

    public void sendMessage(String to, String title, String body) {
        sendMessage(new EmailRequest(to, title, body));
    }

    private void sendMessage(EmailRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(email);
        message.setTo(request.to());
        message.setSubject(request.subject());
        message.setText(request.text());
        send(message);
    }

    private void send(SimpleMailMessage message) {
        emailConfig.send(message);
    }
}