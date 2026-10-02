package com.jangingmall.backend.product.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "exhibition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exhibition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exhibition_id")
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
    private String subtitle;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "banner_image_url", length = 500)
    private String bannerImageUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 기획 순서대로 정렬된 상품 ID. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "exhibition_product", joinColumns = @JoinColumn(name = "exhibition_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "product_id")
    private List<Long> productIds = new ArrayList<>();
}
