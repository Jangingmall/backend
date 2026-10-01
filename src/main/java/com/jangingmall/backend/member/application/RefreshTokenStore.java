package com.jangingmall.backend.member.application;

import java.time.Duration;

public interface RefreshTokenStore {

    void save(Long memberId, String refreshToken, Duration ttl);

    /** 기존 토큰을 지우지 않고 이 토큰을 추가로 허용한다. 같은 계정을 여러 곳에서 동시에 쓰는 테스트 계정용이다. */
    void saveShared(Long memberId, String refreshToken, Duration ttl);

    boolean matches(Long memberId, String refreshToken);

    void delete(Long memberId);
}
