package com.jangingmall.backend.product.infrastructure;

import com.jangingmall.backend.product.domain.ProductImage;
import com.jangingmall.backend.product.domain.ProductImageRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface ProductImageJpaRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(Long productId);

    void deleteByProductId(Long productId);
}

@Repository
class JpaProductImageRepository implements ProductImageRepository {

    private final ProductImageJpaRepository jpa;

    JpaProductImageRepository(ProductImageJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<ProductImage> findByProductIdOrderByDisplayOrderAsc(Long productId) {
        return jpa.findByProductIdOrderByDisplayOrderAsc(productId);
    }

    @Override
    public void deleteByProductId(Long productId) {
        jpa.deleteByProductId(productId);
    }

    @Override
    public List<ProductImage> saveAll(Iterable<ProductImage> images) {
        return jpa.saveAll(images);
    }
}
