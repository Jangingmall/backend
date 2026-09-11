package com.jangingmall.backend.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_delivery", uniqueConstraints = @UniqueConstraint(name = "uk_order_delivery_order_id", columnNames = "order_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_delivery_id")
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "carrier_code", nullable = false, length = 20)
    private String carrierCode;

    @Column(name = "carrier_name", nullable = false, length = 100)
    private String carrierName;

    @Column(name = "tracking_number", nullable = false, length = 100)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OrderDelivery(Long orderId, String carrierCode, String carrierName, String trackingNumber) {
        this.orderId = orderId;
        this.carrierCode = carrierCode;
        this.carrierName = carrierName;
        this.trackingNumber = trackingNumber;
        this.status = DeliveryStatus.SHIPPED;
        this.updatedAt = Instant.now();
    }

    public void updateStatus(DeliveryStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
