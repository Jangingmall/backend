package com.jangingmall.backend.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.jangingmall.backend.member.application.EmailVerificationProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpVerificationEmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendsVerificationLinkWithoutExposingTheTokenAnywhereElse() {
        EmailVerificationProperties properties = new EmailVerificationProperties(
            URI.create("https://api.example.com/api/member/email-verifications/verify"),
            URI.create("https://example.com/"),
            "no-reply@example.com",
            1800
        );
        SmtpVerificationEmailSender sender = new SmtpVerificationEmailSender(mailSender, properties);

        sender.send("artisan@example.com", "verification-token");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@example.com");
        assertThat(message.getTo()).containsExactly("artisan@example.com");
        assertThat(message.getText()).contains(
            "https://api.example.com/api/member/email-verifications/verify?token=verification-token"
        );
    }
}
