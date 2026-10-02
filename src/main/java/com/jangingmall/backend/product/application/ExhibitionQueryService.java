package com.jangingmall.backend.product.application;

import com.jangingmall.backend.global.exception.NotFoundException;
import com.jangingmall.backend.product.domain.Exhibition;
import com.jangingmall.backend.product.domain.ExhibitionProductSort;
import com.jangingmall.backend.product.domain.ExhibitionRepository;
import com.jangingmall.backend.product.domain.ExhibitionSort;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductRepository;
import com.jangingmall.backend.product.domain.ProductStatus;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExhibitionQueryService {

    private final ExhibitionRepository exhibitionRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<ExhibitionResponse.Summary> findAll(String sort) {
        return exhibitionRepository.findAllActive(ExhibitionSort.from(sort)).stream()
            .map(ExhibitionResponse.Summary::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public ExhibitionResponse.Detail findById(Long exhibitionId, String sort) {
        Exhibition exhibition = exhibitionRepository.findActiveById(exhibitionId)
            .orElseThrow(() -> new NotFoundException("기획전을 찾을 수 없습니다"));
        ExhibitionProductSort productSort = ExhibitionProductSort.from(sort);
        List<Product> products = exhibition.getProductIds().stream()
            .map(productRepository::findById)
            .flatMap(java.util.Optional::stream)
            .filter(product -> product.getStatus() == ProductStatus.ON_SALE)
            .sorted(comparator(productSort))
            .toList();
        return new ExhibitionResponse.Detail(exhibition.getId(), exhibition.getTitle(), exhibition.getSubtitle(),
            exhibition.getDescription(), exhibition.getBannerImageUrl(), productSort.name(),
            products.stream().map(ProductResponse::from).toList());
    }

    /** CURATED 는 stream 의 기존 순서(기획 순서)를 그대로 쓴다. */
    private static Comparator<Product> comparator(ExhibitionProductSort sort) {
        return switch (sort) {
            case CURATED -> (a, b) -> 0;
            case NEWEST -> Comparator.comparing(Product::getCreatedAt).reversed().thenComparing(Product::getId, Comparator.reverseOrder());
            case PRICE_ASC -> Comparator.comparingInt(Product::getPrice).thenComparing(Product::getId, Comparator.reverseOrder());
            case PRICE_DESC -> Comparator.comparingInt(Product::getPrice).reversed().thenComparing(Product::getId, Comparator.reverseOrder());
        };
    }
}
