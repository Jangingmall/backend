package com.jangingmall.backend.product.application;

import com.jangingmall.backend.product.domain.Exhibition;
import java.util.List;

public final class ExhibitionResponse {

    private ExhibitionResponse() {
    }

    /** 기획전 목록 한 줄. */
    public record Summary(Long exhibitionId, String title, String subtitle, String bannerImageUrl, int productCount) {
        static Summary from(Exhibition exhibition) {
            return new Summary(exhibition.getId(), exhibition.getTitle(), exhibition.getSubtitle(),
                exhibition.getBannerImageUrl(), exhibition.getProductIds().size());
        }
    }

    /** 기획전 상세: 소개와 판매 중인 상품 목록. */
    public record Detail(Long exhibitionId, String title, String subtitle, String description, String bannerImageUrl,
                         String sort, List<ProductResponse> products) {
    }
}
