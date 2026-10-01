package com.jangingmall.backend.content.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI는 사진을 URL로 내려받는다. 요청의 images는 URL, 이미지 ID처럼 여러 형태로 올 수 있으므로
 * 항목마다 {@link ImageReferenceProvider}를 순서대로 시도해 처음 URL을 만든 구현의 값을 쓴다(URL·ID가 섞여도 순서를 유지한다).
 * 찾을 수 없는 항목은 원본을 유지해 이후 제출 단계에서 기존처럼 이미지 오류로 재제출 대기되게 한다.
 */
@Slf4j
@Component
public class GenerationImageResolver {

    private final List<ImageReferenceProvider> providers;

    public GenerationImageResolver(List<ImageReferenceProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public List<String> resolve(List<String> images) {
        if (images == null || images.isEmpty()) {
            return images;
        }
        return images.stream().map(this::resolveOne).toList();
    }

    private String resolveOne(String reference) {
        if (reference == null || reference.isBlank()) {
            return reference;
        }
        for (ImageReferenceProvider provider : providers) {
            if (!provider.supports(reference)) {
                continue;
            }
            var url = provider.toUrl(reference);
            if (url.isPresent()) {
                return url.get();
            }
        }
        log.warn("사진 참조를 URL로 바꾸지 못했습니다 reference={}", reference);
        return reference;
    }
}
