package com.jangingmall.backend.member.application;

import java.time.Duration;
import java.util.Optional;

public interface OneTimeTokenStore {
    String issue(String purpose, String payload, Duration ttl);
    Optional<String> consume(String purpose, String token);
}
