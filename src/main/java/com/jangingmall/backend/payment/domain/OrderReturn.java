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
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "order_return", uniqueConstraints = @UniqueConstraint(name = "uk_order_return_order_id", columnNames = "order_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "return_id")
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnReason reason;

    @Column(name = "reason_detail", length = 500)
    private String reasonDetail;

    @Column(name = "return_address_id", nullable = false)
    private Long returnAddressId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "order_item_ids", nullable = false, columnDefinition = "jsonb")
    private String orderItemIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_ids", columnDefinition = "jsonb")
    private String imageIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public OrderReturn(Long orderId, ReturnType type, ReturnReason reason, String reasonDetail,
                       Long returnAddressId, String orderItemIds, String imageIds) {
        this.orderId = orderId;
        this.type = type;
        this.reason = reason;
        this.reasonDetail = reasonDetail;
        this.returnAddressId = returnAddressId;
        this.orderItemIds = orderItemIds;
        this.imageIds = imageIds;
        this.status = ReturnStatus.REQUESTED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public OrderReturn(Long orderId, ReturnType type, ReturnReason reason, String reasonDetail,
                       Long returnAddressId, String imageIds) {
        this(orderId, type, reason, reasonDetail, returnAddressId, "[]", imageIds);
    }
}
