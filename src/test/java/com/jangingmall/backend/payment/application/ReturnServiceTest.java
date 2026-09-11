package com.jangingmall.backend.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.member.application.MemberAccess;
import com.jangingmall.backend.payment.domain.OrderReturn;
import com.jangingmall.backend.payment.domain.OrderReturnRepository;
import com.jangingmall.backend.payment.domain.OrderStatus;
import com.jangingmall.backend.payment.domain.PurchaseOrder;
import com.jangingmall.backend.payment.domain.PurchaseOrderRepository;
import com.jangingmall.backend.payment.domain.ReturnReason;
import com.jangingmall.backend.payment.domain.ReturnType;
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
class ReturnServiceTest {

    @Mock private MemberAccess memberAccess;
    @Mock private ShippingAddressReader shippingAddresses;
    @Mock private PurchaseOrderRepository orders;
    @Mock private OrderReturnRepository returns;
    @Mock private OrderNotificationPublisher notifications;
    private ReturnService service;

    @BeforeEach
    void setUp() {
        service = new ReturnService(memberAccess, shippingAddresses, orders, returns, notifications);
    }

    @Test
    @DisplayName("PAY-P1-088 결제 완료 주문의 반품 신청은 주문과 반품을 함께 요청 상태로 만든다")
    void requestsReturnForOwnedPaidOrder() {
        PurchaseOrder order = paidOrder(10L, 1L);
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(returns.findByOrderId(10L)).thenReturn(Optional.empty());
        when(returns.save(any(OrderReturn.class))).thenAnswer(invocation -> {
            OrderReturn orderReturn = invocation.getArgument(0);
            ReflectionTestUtils.setField(orderReturn, "id", 20L);
            return orderReturn;
        });

        ReturnService.ReturnData result = service.request(1L, new ReturnService.RequestReturn(10L, ReturnType.RETURN,
            List.of(101L), ReturnReason.DEFECTIVE, "테두리에 금이 있습니다.",
            List.of("01JRETURNIMAGE000000000000"), null));

        assertThat(result.returnId()).isEqualTo(20L);
        assertThat(result.status().name()).isEqualTo("REQUESTED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RETURN_REQUESTED);
        verify(returns).save(any(OrderReturn.class));
        verify(notifications).returnRequested(org.mockito.ArgumentMatchers.eq(order), any(OrderReturn.class));
    }

    @Test
    @DisplayName("PAY-P1-092 기타 사유에 상세 설명이 없으면 반품 신청을 거부한다")
    void rejectsOtherReasonWithoutDescription() {
        assertThatThrownBy(() -> service.request(1L, new ReturnService.RequestReturn(10L, ReturnType.RETURN,
            List.of(101L), ReturnReason.OTHER, " ", List.of(), null)))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("PAY-P1-096 같은 주문에 이미 반품이 있으면 충돌로 처리한다")
    void rejectsDuplicateReturnRequest() {
        PurchaseOrder order = paidOrder(10L, 1L);
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(returns.findByOrderId(10L)).thenReturn(Optional.of(new OrderReturn(10L, ReturnType.RETURN,
            ReturnReason.CHANGE_OF_MIND, null, 9L, "[]")));

        assertThatThrownBy(() -> service.request(1L, new ReturnService.RequestReturn(10L, ReturnType.RETURN,
            List.of(101L), ReturnReason.CHANGE_OF_MIND, null, List.of(), null)))
            .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("PAY-P1-089/090 결제 전 주문 또는 다른 회원 주문은 반품 신청할 수 없다")
    void rejectsUnpaidOrForeignOrder() {
        PurchaseOrder unpaid = new PurchaseOrder("ORD-UNPAID", 1L,
            new PurchaseOrder.ShippingAddress(9L, "홍길동", "01012345678", "03187", "서울", "101호"),
            null, null, List.of(new PurchaseOrder.OrderLine(7L, "청자", 10_000L, 1, 1, "{}")));
        ReflectionTestUtils.setField(unpaid, "id", 10L);
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(unpaid));
        assertThatThrownBy(() -> service.request(1L, new ReturnService.RequestReturn(10L, ReturnType.RETURN,
            List.of(101L), ReturnReason.CHANGE_OF_MIND, null, List.of(), null)))
            .isInstanceOf(BusinessRuleViolationException.class);

        when(orders.findByIdForUpdate(11L)).thenReturn(Optional.of(paidOrder(11L, 2L)));
        assertThatThrownBy(() -> service.request(1L, new ReturnService.RequestReturn(11L, ReturnType.RETURN,
            List.of(101L), ReturnReason.CHANGE_OF_MIND, null, List.of(), null)))
            .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("PAY-P1-091 주문에 속하지 않은 주문상품은 반품 대상으로 선택할 수 없다")
    void rejectsForeignOrderItem() {
        PurchaseOrder order = paidOrder(10L, 1L);
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(returns.findByOrderId(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.request(1L, new ReturnService.RequestReturn(10L, ReturnType.RETURN,
            List.of(999L), ReturnReason.CHANGE_OF_MIND, null, List.of(), null)))
            .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("PAY-P1-093 불량 반품에 사진이 없으면 422다")
    void requiresPhotoForDefect() {
        assertThatThrownBy(() -> service.request(1L, new ReturnService.RequestReturn(10L, ReturnType.RETURN,
            List.of(101L), ReturnReason.DEFECTIVE, "불량", List.of(), null)))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("PAY-P1-094 반품 사진은 중복 없이 최대 5장이다")
    void limitsReturnPhotos() {
        assertThatThrownBy(() -> service.request(1L, new ReturnService.RequestReturn(10L, ReturnType.RETURN,
            List.of(101L), ReturnReason.CHANGE_OF_MIND, null,
            List.of("1", "2", "3", "4", "5", "6"), null)))
            .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> service.request(1L, new ReturnService.RequestReturn(10L, ReturnType.RETURN,
            List.of(101L), ReturnReason.CHANGE_OF_MIND, null, List.of("1", "1"), null)))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    private PurchaseOrder paidOrder(Long id, Long memberId) {
        PurchaseOrder order = new PurchaseOrder("ORD-20260909-000001", memberId,
            new PurchaseOrder.ShippingAddress(9L, "홍길동", "01012345678", "03187", "서울", "101호"), null, null,
            List.of(new PurchaseOrder.OrderLine(7L, "청자 다완", 12_500L, 1, 14, "{}")));
        ReflectionTestUtils.setField(order, "id", id);
        ReflectionTestUtils.setField(order.getItems().getFirst(), "id", 101L);
        order.markPaid();
        return order;
    }
}
