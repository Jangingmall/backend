package com.jangingmall.backend.member.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberOrderItemView {
    @Id @Column(name = "order_item_id") private Long id;
    @Column(name = "order_id") private Long orderId;
    @Column(name = "product_id") private Long productId;
    @Column(name = "product_name_snapshot") private String productName;
    @Column(name = "price_snapshot") private long price;
    @Column(name = "quantity") private int quantity;
}
