package com.jangingmall.backend.image.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidGenerator {

    public String next() {
        return UUID.randomUUID().toString();
    }
}
