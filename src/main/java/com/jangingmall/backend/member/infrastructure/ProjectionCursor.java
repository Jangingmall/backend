package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

record ProjectionCursor(BigDecimal value, long id) {
    static ProjectionCursor parse(String cursor, String scope, int limit) {
        if (limit < 1 || limit > 100) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        return Optional.ofNullable(cursor).map(encoded -> decode(encoded, scope))
            .orElseGet(() -> new ProjectionCursor(new BigDecimal("999999999999999999999999"), Long.MAX_VALUE));
    }

    String encode(String scope) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            (scope + "|" + value.toPlainString() + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    private static ProjectionCursor decode(String encoded, String scope) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 3 || !parts[0].equals(scope) || Long.parseLong(parts[2]) < 1) {
                throw new IllegalArgumentException();
            }
            return new ProjectionCursor(new BigDecimal(parts[1]), Long.parseLong(parts[2]));
        } catch (IllegalArgumentException exception) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
    }
}
