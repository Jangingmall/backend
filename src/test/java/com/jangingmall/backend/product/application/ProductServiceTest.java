package com.jangingmall.backend.product.application;

import com.jangingmall.backend.content.domain.AiContentClient;
import com.jangingmall.backend.content.domain.InterviewRepository;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductRepository;
import com.jangingmall.backend.product.domain.ProductStatus;
import com.jangingmall.backend.product.domain.CategoryRepository;
import com.jangingmall.backend.product.domain.SubcategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private SubcategoryRepository subcategoryRepository;
    @Mock
    private AiContentClient aiContentClient;
    @Mock
    private InterviewRepository interviewRepository;

    @Captor
    private ArgumentCaptor<Product> productCaptor;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryRepository, subcategoryRepository, aiContentClient, interviewRepository);
    }

    @Test
    @DisplayName("상품 등록 시 DRAFT 상태로 저장된다")
    void createProduct() {
        ProductCommand.Create command = new ProductCommand.Create(1L, null, null, "청자 다완", "설명", 85000, 10, null, List.of(), List.of(), null, List.of());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 1L);
            return p;
        });

        ProductResponse response = productService.create(command);

        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.productId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 상품 조회 시 NotFoundException이 발생한다")
    void findByIdNotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(999L))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("장인 본인의 상품 목록을 페이징으로 조회할 수 있다")
    void findByArtisan() {
        Product product = Product.create(1L, null, null, "제목", "설명", 1000, 5, null);
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findByArtisanId(eq(1L), any())).thenReturn(page);

        Page<ProductResponse> result = productService.findByArtisan(1L, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("타 장인의 상품을 삭제하면 ForbiddenException이 발생한다")
    void deleteByNonOwner() {
        Product product = Product.create(1L, null, null, "제목", "설명", 1000, 5, null);
        ReflectionTestUtils.setField(product, "id", 1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.delete(1L, 999L))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("존재하지 않는 상품 상태 변경 시 NotFoundException이 발생한다")
    void changeStatusNotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.changeStatus(new ProductCommand.ChangeStatus(999L, 1L, "ON_SALE")))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("타 장인이 상태를 변경하면 ForbiddenException이 발생한다")
    void changeStatusForbidden() {
        Product product = Product.create(1L, null, null, "제목", "설명", 1000, 5, null);
        ReflectionTestUtils.setField(product, "id", 1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.changeStatus(new ProductCommand.ChangeStatus(1L, 999L, "ON_SALE")))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("판매 중 상품 목록을 필터와 함께 페이징으로 조회할 수 있다")
    void findOnSale() {
        Product product = Product.create(1L, null, null, "제목", "설명", 1000, 5, null);
        ReflectionTestUtils.setField(product, "status", ProductStatus.ON_SALE);
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findOnSale(any(), any())).thenReturn(page);

        ProductCommand.Search search = new ProductCommand.Search(null, null, null, null, null, null, null, null, null);
        Page<ProductResponse> result = productService.findOnSale(search, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).status()).isEqualTo("ON_SALE");
    }

    @Test
    @DisplayName("상태 변경 성공 — DRAFT에서 ON_SALE로 전이된다")
    void changeStatusSuccess() {
        Product product = Product.create(1L, null, null, "제목", "설명", 1000, 5, null);
        ReflectionTestUtils.setField(product, "id", 1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productService.changeStatus(new ProductCommand.ChangeStatus(1L, 1L, "ON_SALE"));

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }
}
