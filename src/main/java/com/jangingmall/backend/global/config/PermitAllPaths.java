package com.jangingmall.backend.global.config;

import java.util.Set;

public final class PermitAllPaths {

    public static final Set<String> PATHS = Set.of(
        "/api/health"
    );

    private PermitAllPaths() {}
}
