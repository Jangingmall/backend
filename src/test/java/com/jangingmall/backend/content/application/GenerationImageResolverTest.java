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

    @Test
    @DisplayName("이미지 ID는 1280w 공개 URL로 바꾼다")
    void resolvesImageIdTo1280wUrl() {
        when(imageService.variantUrl("01M3TWS18T2H5A8AP6HCDBHRWW", "1280w")).thenReturn(Optional.of(CDN_URL));

        List<String> result = new GenerationImageResolver(imageService).resolve(List.of("01M3TWS18T2H5A8AP6HCDBHRWW"));

        assertThat(result).containsExactly(CDN_URL);
    }

    @Test
    @DisplayName("이미 http(s) URL이면 그대로 두고 조회하지 않는다")
    void keepsUrls() {
        List<String> result = new GenerationImageResolver(imageService)
            .resolve(List.of(CDN_URL, "HTTP://example.com/a.png"));

        assertThat(result).containsExactly(CDN_URL, "HTTP://example.com/a.png");
        verify(imageService, never()).variantUrl(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("URL과 ID가 섞여 있어도 순서를 유지한 채 ID만 바꾼다")
    void mixedKeepsOrder() {
        when(imageService.variantUrl("id-2", "1280w")).thenReturn(Optional.of("https://cdn/2/1280w.webp"));

        List<String> result = new GenerationImageResolver(imageService)
            .resolve(List.of("https://cdn/1.webp", "id-2", "https://cdn/3.webp"));

        assertThat(result).containsExactly("https://cdn/1.webp", "https://cdn/2/1280w.webp", "https://cdn/3.webp");
    }

    @Test
    @DisplayName("찾을 수 없는 ID는 원본을 유지해 이후 제출 단계에서 이미지 오류로 처리되게 한다")
    void unknownIdIsKept() {
        when(imageService.variantUrl("missing", "1280w")).thenReturn(Optional.empty());

        List<String> result = new GenerationImageResolver(imageService).resolve(List.of("missing"));

        assertThat(result).containsExactly("missing");
    }

    @Test
    @DisplayName("빈 목록은 그대로 돌려준다")
    void emptyList() {
        assertThat(new GenerationImageResolver(imageService).resolve(List.of())).isEmpty();
    }
}
