package com.jangingmall.backend.member.application;

public interface LoginAttemptService {
    void recordFailure(String email);
    void clearFailures(String email);
    boolean isLocked(String email);
}
