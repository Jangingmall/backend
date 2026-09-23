package com.jangingmall.backend.member.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

@Entity
@Immutable
@Subselect("select order_id, order_number, member_id, address_id, recipient_name, recipient_phone, zip_code, address1, address2, payment_method, status, total_amount, shipping_amount, created_at from orders")
@Synchronize("orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberOrderView {
    @Id @Column(name = "order_id") private Long id;
    @Column(name = "order_number") private String orderNumber;
    @Column(name = "member_id") private Long memberId;
    @Column(name = "address_id") private Long addressId;
    @Column(name = "recipient_name") private String recipientName;
    @Column(name = "recipient_phone") private String recipientPhone;
    @Column(name = "zip_code") private String zipCode;
    @Column(name = "address1") private String address1;
    @Column(name = "address2") private String address2;
    @Column(name = "status") private String status;
    @Column(name = "payment_method") private String paymentMethod;
    @Column(name = "total_amount") private long totalAmount;
    @Column(name = "shipping_amount") private long shippingAmount;
    @Column(name = "created_at") private LocalDateTime createdAt;
}
