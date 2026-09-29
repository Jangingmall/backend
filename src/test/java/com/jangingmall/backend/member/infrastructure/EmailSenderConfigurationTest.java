package com.jangingmall.backend.member.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.jangingmall.backend.member.application.EmailSender;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EmailSenderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(EmailSenderConfiguration.class);

    @Test
    void productionWithoutSmtpDoesNotSilentlySkipVerificationEmail() {
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
            .run(context -> {
                assertThat(context).hasSingleBean(EmailSender.class);
                assertThatThrownBy(() -> context.getBean(EmailSender.class)
                    .sendVerificationCode("member@example.com", "123456"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SMTP is not configured");
            });
    }

    @Test
    void localWithoutSmtpKeepsNoOpSenderForDevelopment() {
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
            .run(context -> {
                assertThat(context).hasSingleBean(EmailSender.class);
                context.getBean(EmailSender.class).sendVerificationCode("member@example.com", "123456");
            });
    }

    @Test
    void productionUsesConfiguredSenderAndFromAddressWhenAvailable() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        contextRunner.withBean(JavaMailSender.class, () -> mailSender)
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
            .withPropertyValues("member.email-verification.from=verify@midam.store")
            .run(context -> {
                context.getBean(EmailSender.class).sendVerificationCode("member@example.com", "123456");

                var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
                verify(mailSender).send(message.capture());
                Assertions.assertAll(
                    () -> assertThat(message.getValue().getFrom()).isEqualTo("verify@midam.store"),
                    () -> assertThat(message.getValue().getTo()).containsExactly("member@example.com"),
                    () -> assertThat(message.getValue().getText()).contains("123456")
                );
            });
    }
}
