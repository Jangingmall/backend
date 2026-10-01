package com.jangingmall.backend.product.domain;

import com.jangingmall.backend.global.exception.ForbiddenException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Column(length = 50)
    private String material;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stock;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_gift_theme", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "gift_theme")
    private List<String> giftThemes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_purpose_tag", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "purpose_tag")
    private List<String> purposeTags = new ArrayList<>();

    /**
     * 업로드 이미지(product_image)가 없는 상품의 상세 갤러리용 외부 이미지 주소. 시연·시드 데이터용이며
     * 대표 이미지(thumbnail_url) 뒤에 이어 붙는다.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_detail_image", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "image_url", length = 500)
    private List<String> detailImageUrls = new ArrayList<>();

    @Column(name = "production_period_days")
    private Integer productionPeriodDays;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_color", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "color")
    private List<String> colors = new ArrayList<>();

    @Column(name = "is_limited", nullable = false)
    private boolean isLimited = false;

    @Column(name = "is_custom_order", nullable = false)
    private boolean isCustomOrder = false;

    @Column(name = "is_single_item", nullable = false)
    private boolean isSingleItem = false;

    @Column(name = "has_gift_wrap", nullable = false)
    private boolean hasGiftWrap = false;

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

    public static Product create(
        Long artisanId,
        Category category,
        Subcategory subcategory,
        String title,
        String description,
        int price,
        int stock,
        String thumbnailUrl,
        List<String> giftThemes,
        List<String> purposeTags,
        Integer productionPeriodDays,
        List<String> colors
    ) {
        Product product = create(artisanId, category, subcategory, title, description, price, stock, thumbnailUrl);
        product.giftThemes = new ArrayList<>(giftThemes);
        product.purposeTags = new ArrayList<>(purposeTags);
        product.productionPeriodDays = productionPeriodDays;
        product.colors = new ArrayList<>(colors);
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
        List<String> giftThemes,
        List<String> purposeTags,
        Integer productionPeriodDays,
        List<String> colors,
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
        this.giftThemes = new ArrayList<>(giftThemes);
        this.purposeTags = new ArrayList<>(purposeTags);
        this.productionPeriodDays = productionPeriodDays;
        this.colors = new ArrayList<>(colors);
    }

    /** 대표 이미지가 아직 없을 때만 채운다(판매자가 정한 대표 이미지는 덮어쓰지 않는다). */
    public void useThumbnail(String url) {
        if (url != null && !url.isBlank() && url.length() <= 500 && (thumbnailUrl == null || thumbnailUrl.isBlank())) {
            thumbnailUrl = url;
        }
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
