package com.jangingmall.backend.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    private String productNameSnapshot;

    @Column(name = "price_snapshot", nullable = false)
    private long priceSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "total_price", nullable = false)
    private long totalPrice;

    @Column(name = "production_period_days_snapshot")
    private Integer productionPeriodDaysSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_options_snapshot", columnDefinition = "jsonb")
    private String selectedOptionsSnapshot;

    OrderItem(PurchaseOrder order, PurchaseOrder.OrderLine line) {
        this.order = order;
        this.productId = line.productId();
        this.productNameSnapshot = line.productName();
        this.priceSnapshot = line.unitPrice();
        this.quantity = line.quantity();
        this.totalPrice = line.totalPrice();
        this.productionPeriodDaysSnapshot = line.productionPeriodDays();
        this.selectedOptionsSnapshot = line.selectedOptions();
    }
}
