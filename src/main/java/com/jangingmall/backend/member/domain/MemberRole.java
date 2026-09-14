package com.jangingmall.backend.member.domain;

import java.util.List;

public enum MemberRole {
    USER(List.of("ROLE_USER")),
    ARTISAN(List.of("ROLE_USER", "ROLE_ARTISAN")),
    ADMIN(List.of("ROLE_USER", "ROLE_ADMIN"));

    private final List<String> authorities;

    MemberRole(List<String> authorities) {
        this.authorities = authorities;
    }

    public List<String> authorities() {
        return authorities;
    }

    public boolean allows(MemberRole requiredRole) {
        return authorities.contains("ROLE_" + requiredRole.name());
    }
}
