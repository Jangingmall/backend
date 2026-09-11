package com.jangingmall.backend.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.member.application.MemberAccess;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.payment.domain.DeliveryStatus;
import com.jangingmall.backend.payment.domain.OrderDelivery;
import com.jangingmall.backend.payment.domain.OrderDeliveryRepository;
import com.jangingmall.backend.payment.domain.PurchaseOrder;
import com.jangingmall.backend.payment.domain.PurchaseOrderRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock private MemberAccess memberAccess;
    @Mock private PurchaseOrderRepository orders;
    @Mock private OrderDeliveryRepository deliveries;
    @Mock private DeliveryTrackingGateway trackingGateway;
    private DeliveryService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryService(memberAccess, orders, deliveries, trackingGateway);
    }

    @Test
    @DisplayName("본인 주문의 배송 조회는 스마트택배 최신 상태를 반환하고 저장한다")
    void returnsCurrentTrackingStatus() {
        PurchaseOrder order = paidOrder(10L, 1L);
        OrderDelivery delivery = new OrderDelivery(10L, "04", "CJ대한통운", "1234567890");
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(deliveries.findByOrderId(10L)).thenReturn(Optional.of(delivery));
        when(trackingGateway.track("04", "1234567890"))
            .thenReturn(new DeliveryTrackingGateway.TrackingSnapshot(DeliveryStatus.DELIVERED));

        DeliveryService.DeliveryData result = service.get(1L, 10L);

        assertThat(result.carrier()).isEqualTo("CJ대한통운");
        assertThat(result.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(com.jangingmall.backend.payment.domain.OrderStatus.DELIVERED);
        verify(memberAccess).active(1L);
    }

    @Test
    @DisplayName("PAY-P1-004 배송정보가 아직 없으면 404다")
    void returnsNotFoundWithoutDelivery() {
        PurchaseOrder order = paidOrder(10L, 1L);
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(deliveries.findByOrderId(10L)).thenReturn(Optional.empty());

        assertNotFound(() -> service.get(1L, 10L));
    }

    @Test
    @DisplayName("PAY-P1-005 다른 회원 주문의 배송정보는 404로 감춘다")
    void hidesForeignDelivery() {
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(paidOrder(10L, 2L)));
        assertNotFound(() -> service.get(1L, 10L));
    }

    @Test
    @DisplayName("PAY-P1-006 존재하지 않는 주문의 배송조회는 404다")
    void rejectsUnknownOrder() {
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.empty());
        assertNotFound(() -> service.get(1L, 10L));
    }

    private PurchaseOrder paidOrder(Long id, Long memberId) {
        PurchaseOrder order = new PurchaseOrder("ORD-20260909-000001", memberId,
            new PurchaseOrder.ShippingAddress(9L, "홍길동", "01012345678", "03187", "서울", "101호"), null, null,
            List.of(new PurchaseOrder.OrderLine(7L, "청자 다완", 12_500L, 1, 14, "{}")));
        ReflectionTestUtils.setField(order, "id", id);
        order.markPaid();
        return order;
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOf(DomainException.class)
            .extracting(error -> ((DomainException) error).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
    }
}
