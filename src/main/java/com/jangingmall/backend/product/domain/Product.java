package com.jangingmall.backend.product.domain;

import com.jangingmall.backend.global.exception.ForbiddenException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @Column(name = "artisan_id", nullable = false)
    private Long artisanId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id")
    private Subcategory subcategory;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stock;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Product create(
        Long artisanId,
        Category category,
        Subcategory subcategory,
        String title,
        String description,
        int price,
        int stock,
        String thumbnailUrl
    ) {
        validatePrice(price);
        validateStock(stock);
        Product product = new Product();
        product.artisanId = artisanId;
        product.category = category;
        product.subcategory = subcategory;
        product.title = title;
        product.description = description;
        product.price = price;
        product.stock = stock;
        product.thumbnailUrl = thumbnailUrl;
        product.status = ProductStatus.DRAFT;
        return product;
    }

    public void update(
        Category category,
        Subcategory subcategory,
        String title,
        String description,
        int price,
        int stock,
        String thumbnailUrl,
        Long requesterId
    ) {
        verifyOwner(requesterId);
        validatePrice(price);
        validateStock(stock);
        this.category = category;
        this.subcategory = subcategory;
        this.title = title;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.thumbnailUrl = thumbnailUrl;
    }

    public void changeStatus(ProductStatus next, Long requesterId) {
        verifyOwner(requesterId);
        status.validateTransitionTo(next);
        status = next;
    }

    public void verifyOwner(Long requesterId) {
        if (!artisanId.equals(requesterId)) {
            throw new ForbiddenException(ProductErrorMessage.FORBIDDEN.message());
        }
    }

    private static void validatePrice(int price) {
        if (price <= 0) {
            throw new com.jangingmall.backend.global.exception.BusinessRuleViolationException(ProductErrorMessage.INVALID_PRICE.message());
        }
    }

    private static void validateStock(int stock) {
        if (stock < 0) {
            throw new com.jangingmall.backend.global.exception.BusinessRuleViolationException(ProductErrorMessage.INVALID_STOCK.message());
        }
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
