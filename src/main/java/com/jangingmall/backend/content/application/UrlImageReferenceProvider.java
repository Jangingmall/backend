package com.jangingmall.backend.content.application;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/** 이미 http(s) URL인 참조는 그대로 쓴다. */
@Component
@Order(1)
public class UrlImageReferenceProvider implements ImageReferenceProvider {

    @Override
    public boolean supports(String reference) {
        String lower = reference.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    @Override
    public Optional<String> toUrl(String reference) {
        return Optional.of(reference);
    }
}
