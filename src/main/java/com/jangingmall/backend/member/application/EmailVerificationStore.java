package com.jangingmall.backend.member.application;

import java.time.Duration;
import java.util.Optional;

public interface EmailVerificationStore {
    void save(String email, String code, Duration ttl);
    Optional<String> consumeCode(String email);
}
