package com.jangingmall.backend.revalidate.domain;

public enum RevalidateEventType {
    PRODUCT_CREATED("product.created"),
    PRODUCT_UPDATED("product.updated"),
    PRODUCT_STATUS_CHANGED("product.statusChanged"),
    PRODUCT_DELETED("product.deleted"),
    PRODUCT_CONTENT_PUBLISHED("product.contentPublished"),
    ARTISAN_UPDATED("artisan.updated"),
    ARTISAN_STATUS_CHANGED("artisan.statusChanged");

    private final String value;

    RevalidateEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
