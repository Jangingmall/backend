package com.jangingmall.backend.payment.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(
    name = "uk_orders_member_client_request", columnNames = {"member_id", "client_request_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 64)
    private String orderNumber;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "address_id")
    private Long addressId;

    @Column(name = "recipient_name", nullable = false, length = 50)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(name = "zip_code", nullable = false, length = 10)
    private String zipCode;

    @Column(name = "address1", nullable = false, length = 255)
    private String address1;

    @Column(name = "address2", length = 255)
    private String address2;

    @Column(name = "delivery_request", length = 100)
    private String deliveryRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "shipping_amount", nullable = false)
    private long shippingAmount;

    @Column(name = "client_request_key", length = 100)
    private String clientRequestKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public PurchaseOrder(String orderNumber, Long memberId, ShippingAddress address, String deliveryRequest,
                         PaymentMethod paymentMethod, String clientRequestKey, List<OrderLine> orderLines,
                         long shippingAmount) {
        this.orderNumber = orderNumber;
        this.memberId = memberId;
        this.addressId = address.addressId();
        this.recipientName = address.recipientName();
        this.recipientPhone = address.phone();
        this.zipCode = address.zipCode();
        this.address1 = address.address1();
        this.address2 = address.address2();
        this.deliveryRequest = deliveryRequest;
        this.paymentMethod = paymentMethod;
        this.status = OrderStatus.CREATED;
        this.clientRequestKey = clientRequestKey;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.shippingAmount = shippingAmount;
        long itemAmount = orderLines.stream().map(OrderLine::totalPrice).reduce(0L, Math::addExact);
        this.totalAmount = Math.addExact(itemAmount, shippingAmount);
        orderLines.forEach(line -> items.add(new OrderItem(this, line)));
    }

    public PurchaseOrder(String orderNumber, Long memberId, ShippingAddress address, String deliveryRequest,
                         PaymentMethod paymentMethod, String clientRequestKey, List<OrderLine> orderLines) {
        this(orderNumber, memberId, address, deliveryRequest, paymentMethod, clientRequestKey, orderLines, 0L);
    }

    public PurchaseOrder(String orderNumber, Long memberId, ShippingAddress address, String deliveryRequest,
                         String clientRequestKey, List<OrderLine> orderLines) {
        this(orderNumber, memberId, address, deliveryRequest, PaymentMethod.CARD, clientRequestKey, orderLines);
    }

    public void markPaid() {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException("결제 대기 상태의 주문만 결제 완료로 변경할 수 있습니다.");
        }
        status = OrderStatus.PAID;
        updatedAt = Instant.now();
    }

    public void markPaymentFailed() {
        if (status != OrderStatus.CREATED) {
            throw new IllegalStateException("결제 대기 상태의 주문만 결제 실패로 변경할 수 있습니다.");
        }
        status = OrderStatus.PAYMENT_FAILED;
        updatedAt = Instant.now();
    }

    public void cancel() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("결제 완료 주문만 취소할 수 있습니다.");
        }
        status = OrderStatus.CANCELED;
        canceledAt = Instant.now();
        updatedAt = canceledAt;
    }

    public void markDelivered() {
        if (status != OrderStatus.PAID) {
            throw new IllegalStateException("결제 완료 주문만 배송 완료로 변경할 수 있습니다.");
        }
        status = OrderStatus.DELIVERED;
        updatedAt = Instant.now();
    }

    public void requestReturn() {
        if (status != OrderStatus.PAID && status != OrderStatus.DELIVERED) {
            throw new IllegalStateException("결제 완료 또는 배송 완료 주문만 반품 신청할 수 있습니다.");
        }
        status = OrderStatus.RETURN_REQUESTED;
        updatedAt = Instant.now();
    }

    public record ShippingAddress(Long addressId, String recipientName, String phone, String zipCode,
                                  String address1, String address2) {}

    public record OrderLine(Long productId, String productName, long unitPrice, int quantity,
                            Integer productionPeriodDays, String selectedOptions) {
        public long totalPrice() {
            return Math.multiplyExact(unitPrice, quantity);
        }
    }
}
