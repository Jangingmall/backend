package com.jangingmall.backend.content.application;

import com.jangingmall.backend.image.application.ImageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationImageResolverTest {

    private static final String CDN_URL = "https://img.stg.midam.store/images/product/63/01M3TWS18T2H5A8AP6HCDBHRWW/1280w.webp";

    @Mock
    private ImageService imageService;

    private GenerationImageResolver resolver() {
        return new GenerationImageResolver(List.of(
            new UrlImageReferenceProvider(), new ImageIdReferenceProvider(imageService)));
    }

    @Test
    @DisplayName("이미지 ID는 1280w 공개 URL로 바꾼다")
    void resolvesImageIdTo1280wUrl() {
        when(imageService.variantUrl("01M3TWS18T2H5A8AP6HCDBHRWW", "1280w")).thenReturn(Optional.of(CDN_URL));

        assertThat(resolver().resolve(List.of("01M3TWS18T2H5A8AP6HCDBHRWW"))).containsExactly(CDN_URL);
    }

    @Test
    @DisplayName("이미 http(s) URL이면 그대로 두고 이미지 ID 조회를 하지 않는다")
    void keepsUrls() {
        List<String> result = resolver().resolve(List.of(CDN_URL, "HTTP://example.com/a.png"));

        assertThat(result).containsExactly(CDN_URL, "HTTP://example.com/a.png");
        verify(imageService, never()).variantUrl(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("URL과 ID가 섞여 있어도 순서를 유지한 채 ID만 바꾼다")
    void mixedKeepsOrder() {
        when(imageService.variantUrl("id-2", "1280w")).thenReturn(Optional.of("https://cdn/2/1280w.webp"));

        List<String> result = resolver().resolve(List.of("https://cdn/1.webp", "id-2", "https://cdn/3.webp"));

        assertThat(result).containsExactly("https://cdn/1.webp", "https://cdn/2/1280w.webp", "https://cdn/3.webp");
    }

    @Test
    @DisplayName("찾을 수 없는 ID는 원본을 유지해 이후 제출 단계에서 이미지 오류로 처리되게 한다")
    void unknownIdIsKept() {
        when(imageService.variantUrl("missing", "1280w")).thenReturn(Optional.empty());

        assertThat(resolver().resolve(List.of("missing"))).containsExactly("missing");
    }

    @Test
    @DisplayName("빈 목록은 그대로 돌려준다")
    void emptyList() {
        assertThat(resolver().resolve(List.of())).isEmpty();
    }

    @Test
    @DisplayName("새 참조 형태는 ImageReferenceProvider 구현을 추가하는 것만으로 처리된다")
    void customProviderExtendsFormats() {
        ImageReferenceProvider objectKeyProvider = new ImageReferenceProvider() {
            @Override
            public boolean supports(String reference) {
                return reference.startsWith("images/");
            }

            @Override
            public Optional<String> toUrl(String reference) {
                return Optional.of("https://cdn/" + reference);
            }
        };
        GenerationImageResolver resolver = new GenerationImageResolver(List.of(
            new UrlImageReferenceProvider(), objectKeyProvider, new ImageIdReferenceProvider(imageService)));

        assertThat(resolver.resolve(List.of("images/product/1/a/1280w.webp")))
            .containsExactly("https://cdn/images/product/1/a/1280w.webp");
        verify(imageService, never()).variantUrl(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("앞선 구현이 URL을 만들지 못하면 다음 구현을 시도한다")
    void fallsThroughToNextProvider() {
        ImageReferenceProvider failing = new ImageReferenceProvider() {
            @Override
            public boolean supports(String reference) {
                return true;
            }

            @Override
            public Optional<String> toUrl(String reference) {
                return Optional.empty();
            }
        };
        when(imageService.variantUrl("id-9", "1280w")).thenReturn(Optional.of("https://cdn/9.webp"));
        GenerationImageResolver resolver = new GenerationImageResolver(List.of(
            new UrlImageReferenceProvider(), failing, new ImageIdReferenceProvider(imageService)));

        assertThat(resolver.resolve(List.of("id-9"))).containsExactly("https://cdn/9.webp");
    }
}
