package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

public record PageRequest(long beforeId, int limit) {
    public static PageRequest from(String cursor, int limit) {
        if (limit < 1 || limit > 100) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        return new PageRequest(Optional.ofNullable(cursor).map(PageRequest::decode).orElse(Long.MAX_VALUE), limit);
    }

    public static String encode(long id) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(Long.toString(id).getBytes(StandardCharsets.UTF_8));
    }

    private static long decode(String cursor) {
        try {
            long id = Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
            if (id < 1) {
                throw new IllegalArgumentException();
            }
            return id;
        } catch (IllegalArgumentException exception) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
    }
}
