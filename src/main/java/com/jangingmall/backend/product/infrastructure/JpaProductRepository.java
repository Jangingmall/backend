package com.jangingmall.backend.product.infrastructure;

import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductRepository;
import com.jangingmall.backend.product.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
interface JpaProductRepositoryJpa extends JpaRepository<Product, Long> {
    Page<Product> findByArtisanId(Long artisanId, Pageable pageable);
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    @Query("SELECT DISTINCT p.material FROM Product p WHERE p.subcategory.id = :subcategoryId AND p.material IS NOT NULL")
    List<String> findDistinctMaterialsBySubcategoryId(@Param("subcategoryId") Long subcategoryId);
}

@Repository
class JpaProductRepository implements ProductRepository {

    private final JpaProductRepositoryJpa jpa;

    JpaProductRepository(JpaProductRepositoryJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Product save(Product product) {
        return jpa.save(product);
    }

    @Override
    public Optional<Product> findById(Long productId) {
        return jpa.findById(productId);
    }

    @Override
    public Page<Product> findByArtisanId(Long artisanId, Pageable pageable) {
        return jpa.findByArtisanId(artisanId, pageable);
    }

    @Override
    public Page<Product> findByStatus(ProductStatus status, Pageable pageable) {
        return jpa.findByStatus(status, pageable);
    }

    @Override
    public List<String> findDistinctMaterialsBySubcategoryId(Long subcategoryId) {
        return jpa.findDistinctMaterialsBySubcategoryId(subcategoryId);
    }

    @Override
    public void delete(Product product) {
        jpa.delete(product);
    }
}
