package com.jangingmall.backend.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Slf4j
@Profile({"prod", "local-postgresql"})
@Configuration
public class EmailRealConfig implements EmailConfig {
    @Value("${spring.mail.host}")
    private String host;
    @Value("${spring.mail.port}")
    private int port;
    @Value("${spring.mail.username}")
    private String username;
    @Value("${spring.mail.password}")
    private String password;
    @Value("${spring.mail.transport.protocol}")
    private String protocol;
    @Value("${spring.mail.smtp.auth}")
    private boolean auth;
    @Value("${spring.mail.smtp.starttls.enable}")
    private boolean starttls;

    @Bean
    public JavaMailSender getJavaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        mailSender.setProtocol(protocol);
        Properties props = mailSender.getJavaMailProperties();

        log.info("username = {}", username);
        log.info("host = {}", host);
        log.info("port = {}", port);
        log.info("password  = {}",
                password != null && !password.isBlank());
        props.put("mail.smtp.auth", auth);
        props.put("mail.smtp.starttls.enable", starttls);
        return mailSender;
    }
}
