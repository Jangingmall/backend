package com.jangingmall.backend.member.application;

import java.time.Duration;
import java.util.Optional;

public interface EmailVerificationTokenStore {

    String issue(Long memberId, Duration ttl);

    Optional<Long> consume(String token);
}
