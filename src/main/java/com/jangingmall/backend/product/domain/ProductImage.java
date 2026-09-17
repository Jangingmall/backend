package com.jangingmall.backend.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A product-specific reference to an image aggregate. */
@Entity
@Table(name = "product_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_image_id")
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "image_id", nullable = false, length = 30)
    private String imageId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "alt", length = 200)
    private String alt;

    public ProductImage(Long productId, String imageId, int displayOrder, String alt) {
        this.productId = productId;
        this.imageId = imageId;
        this.displayOrder = displayOrder;
        this.alt = alt;
    }
}
