package com.jangingmall.backend.global.config;

import org.springframework.mail.javamail.JavaMailSender;

public interface EmailConfig {
    JavaMailSender getJavaMailSender();
}
