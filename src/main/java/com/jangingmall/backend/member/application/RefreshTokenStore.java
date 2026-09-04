package com.jangingmall.backend.member.application;

import java.time.Duration;

public interface RefreshTokenStore {

    void save(Long memberId, String refreshToken, Duration ttl);

    boolean matches(Long memberId, String refreshToken);

    void delete(Long memberId);
}
