package com.jangingmall.backend.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.product.domain.Exhibition;
import com.jangingmall.backend.product.domain.ExhibitionRepository;
import com.jangingmall.backend.product.domain.ExhibitionSort;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductRepository;
import com.jangingmall.backend.product.domain.ProductStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExhibitionQueryServiceTest {

    private final ExhibitionRepository exhibitions = mock(ExhibitionRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ExhibitionQueryService service = new ExhibitionQueryService(exhibitions, products);

    private Product product(long id, String title, int price, ProductStatus status) {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(id);
        when(product.getTitle()).thenReturn(title);
        when(product.getPrice()).thenReturn(price);
        when(product.getStatus()).thenReturn(status);
        when(product.getCreatedAt()).thenReturn(java.time.LocalDateTime.of(2026, 9, (int) id, 0, 0));
        when(products.findById(id)).thenReturn(Optional.of(product));
        return product;
    }

    private Exhibition exhibition(Long... productIds) {
        Exhibition exhibition = mock(Exhibition.class);
        when(exhibition.getId()).thenReturn(1L);
        when(exhibition.getTitle()).thenReturn("기획전");
        when(exhibition.getProductIds()).thenReturn(List.of(productIds));
        when(exhibitions.findActiveById(1L)).thenReturn(Optional.of(exhibition));
        return exhibition;
    }

    @Test
    @DisplayName("기본 정렬은 기획 순서이고 판매 중이 아닌 상품은 뺀다")
    void keepsCuratedOrderAndSkipsNotOnSale() {
        product(1, "가", 300, ProductStatus.ON_SALE);
        product(2, "나", 100, ProductStatus.HIDDEN);
        product(3, "다", 200, ProductStatus.ON_SALE);
        exhibition(3L, 2L, 1L);

        ExhibitionResponse.Detail detail = service.findById(1L, null);

        assertThat(detail.sort()).isEqualTo("CURATED");
        assertThat(detail.products()).extracting(ProductResponse::title).containsExactly("다", "가");
    }

    @Test
    @DisplayName("PRICE_ASC·PRICE_DESC 로 가격순 정렬한다")
    void sortsByPrice() {
        product(1, "가", 300, ProductStatus.ON_SALE);
        product(3, "다", 200, ProductStatus.ON_SALE);
        exhibition(1L, 3L);

        assertThat(service.findById(1L, "price-asc").products()).extracting(ProductResponse::title).containsExactly("다", "가");
        assertThat(service.findById(1L, "PRICE_DESC").products()).extracting(ProductResponse::title).containsExactly("가", "다");
    }

    @Test
    @DisplayName("없는 기획전은 404 로 처리한다")
    void throwsWhenMissing() {
        when(exhibitions.findActiveById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(9L, null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("목록은 정렬 문자열을 enum 으로 바꿔 조회한다")
    void listUsesSortEnum() {
        Exhibition exhibition = mock(Exhibition.class);
        when(exhibition.getId()).thenReturn(1L);
        when(exhibition.getProductIds()).thenReturn(List.of(1L, 2L));
        when(exhibitions.findAllActive(ExhibitionSort.NEWEST)).thenReturn(List.of(exhibition));

        assertThat(service.findAll("newest")).extracting(ExhibitionResponse.Summary::productCount).containsExactly(2);
    }
}
