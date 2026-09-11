package com.jangingmall.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JangingmallApplication {

    public static void main(String[] args) {
        SpringApplication.run(JangingmallApplication.class, args);
    }
}
