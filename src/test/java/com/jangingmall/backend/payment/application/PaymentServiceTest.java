package com.jangingmall.backend.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ConcurrentUpdateException;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.global.exception.ExternalServiceException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.PaymentProviderRejectedException;
import com.jangingmall.backend.member.application.MemberAccess;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.payment.domain.Payment;
import com.jangingmall.backend.payment.domain.PaymentMethod;
import com.jangingmall.backend.payment.domain.PaymentRepository;
import com.jangingmall.backend.payment.domain.PaymentStatus;
import com.jangingmall.backend.payment.domain.OrderStatus;
import com.jangingmall.backend.payment.domain.PurchaseOrder;
import com.jangingmall.backend.payment.domain.PurchaseOrderRepository;
import java.time.Instant;
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
class PaymentServiceTest {

    @Mock private MemberAccess memberAccess;
    @Mock private ShippingAddressReader shippingAddresses;
    @Mock private CheckoutCartReader checkoutCart;
    @Mock private CheckoutCatalog catalog;
    @Mock private PurchaseOrderRepository orders;
    @Mock private PaymentRepository payments;
    @Mock private PaymentGateway paymentGateway;
    @Mock private OrderNotificationPublisher notifications;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties();
        properties.setClientKey("test_ck_123");
        service = new PaymentService(memberAccess, shippingAddresses, checkoutCart, catalog, orders, payments, paymentGateway,
            properties, notifications);
    }

    @Test
    @DisplayName("PAY-P0-031/035 주문 생성은 서버 가격과 장인별 배송비로 총액을 산정한다")
    void createsOrderFromCartWithServerQuote() {
        when(checkoutCart.selectedItems(1L, List.of(11L))).thenReturn(List.of(cartLine()));
        when(shippingAddresses.findOwned(1L, 9L)).thenReturn(address());
        when(catalog.quote(any(), any(Integer.class), any(), any())).thenReturn(quote());
        when(orders.save(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 100L);
            return order;
        });

        PaymentService.OrderData result = service.createOrder(1L,
            new PaymentService.CreateOrder(List.of(11L), 9L, "문 앞에 놓아주세요", PaymentMethod.CARD), "checkout-1");

        assertThat(result.orderId()).isEqualTo(100L);
        assertThat(result.totalAmount()).isEqualTo(28_000L);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(7L);
            assertThat(item.price()).isEqualTo(12_500L);
            assertThat(item.quantity()).isEqualTo(2);
        });
        verify(memberAccess).requireRole(1L, MemberRole.USER);
        verify(catalog).quote(7L, 2, List.of(), List.of());
    }

    @Test
    @DisplayName("PAY-P0-076 같은 Idempotency-Key 주문 재시도는 기존 주문을 반환하고 재고를 다시 차감하지 않는다")
    void reusesOrderForSameIdempotencyKey() {
        PurchaseOrder existing = order(100L, 1L, 25_000L);
        when(orders.findByMemberIdAndClientRequestKey(1L, "checkout-1")).thenReturn(Optional.of(existing));

        PaymentService.OrderData result = service.createOrder(1L,
            new PaymentService.CreateOrder(List.of(11L), 9L, null, PaymentMethod.CARD), " checkout-1 ");

        assertThat(result.orderId()).isEqualTo(100L);
        verify(catalog).lockOrderCreation(1L, "checkout-1");
        verify(catalog, never()).reserve(any());
    }

    @Test
    @DisplayName("PAY-P0-077 100자를 넘는 Idempotency-Key는 422로 거부한다")
    void rejectsOversizedIdempotencyKey() {
        assertThatThrownBy(() -> service.createOrder(1L,
            new PaymentService.CreateOrder(List.of(11L), 9L, null, PaymentMethod.CARD), "x".repeat(101)))
            .isInstanceOf(BusinessRuleViolationException.class);
        verify(catalog, never()).lockOrderCreation(any(), any());
    }

    @Test
    @DisplayName("PAY-P0-033/034 재고 부족 또는 판매중지 상품이면 주문을 저장하지 않는다")
    void rejectsUnavailableProductDuringOrderCreation() {
        when(checkoutCart.selectedItems(1L, List.of(11L))).thenReturn(List.of(cartLine()));
        when(shippingAddresses.findOwned(1L, 9L)).thenReturn(address());
        when(catalog.quote(any(), any(Integer.class), any(), any()))
            .thenThrow(new BusinessRuleViolationException("판매 불가"));

        assertThatThrownBy(() -> service.createOrder(1L,
            new PaymentService.CreateOrder(List.of(11L), 9L, null, PaymentMethod.CARD), null))
            .isInstanceOf(BusinessRuleViolationException.class);
        verify(orders, never()).save(any());
    }

    @Test
    @DisplayName("PAY-P0-038 동시 주문 재고 경합에서 조건부 차감 실패 요청은 주문을 생성하지 않는다")
    void rejectsInventoryRaceDuringOrderCreation() {
        when(checkoutCart.selectedItems(1L, List.of(11L))).thenReturn(List.of(cartLine()));
        when(shippingAddresses.findOwned(1L, 9L)).thenReturn(address());
        when(catalog.quote(any(), any(Integer.class), any(), any())).thenReturn(quote());
        doThrow(new BusinessRuleViolationException("재고 경합")).when(catalog).reserve(any());

        assertThatThrownBy(() -> service.createOrder(1L,
            new PaymentService.CreateOrder(List.of(11L), 9L, null, PaymentMethod.CARD), null))
            .isInstanceOf(BusinessRuleViolationException.class);
        verify(orders, never()).save(any());
    }

    @Test
    @DisplayName("결제 준비는 서버가 산정한 주문 금액과 다른 요청을 거부한다")
    void rejectsTamperedPrepareAmount() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        when(orders.findById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.prepare(1L, new PaymentService.Prepare(100L, 1L, PaymentMethod.CARD)))
            .isInstanceOf(BusinessRuleViolationException.class);

        verify(payments, never()).save(any());
    }

    @Test
    @DisplayName("PAY-P0-039 정상 결제 준비는 201용 신규 결제를 반환한다")
    void preparesNewPayment() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        when(orders.findById(100L)).thenReturn(Optional.of(order));
        when(payments.findByOrderId(100L)).thenReturn(Optional.empty());
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", 200L);
            return payment;
        });

        PaymentService.PreparedPayment result = service.prepare(1L,
            new PaymentService.Prepare(100L, 25_000L, PaymentMethod.CARD));

        assertThat(result.created()).isTrue();
        assertThat(result.paymentId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("PAY-P0-040/041 없는 주문과 다른 회원 주문의 결제 준비는 404다")
    void hidesUnknownOrForeignOrderOnPrepare() {
        when(orders.findById(100L)).thenReturn(Optional.empty());
        assertNotFound(() -> service.prepare(1L, new PaymentService.Prepare(100L, 1L, PaymentMethod.CARD)));
        when(orders.findById(101L)).thenReturn(Optional.of(order(101L, 2L, 25_000L)));
        assertNotFound(() -> service.prepare(1L, new PaymentService.Prepare(101L, 25_000L, PaymentMethod.CARD)));
    }

    @Test
    @DisplayName("PAY-P0-042 이미 결제 완료된 주문은 새 결제를 준비하지 않는다")
    void rejectsPaidOrderOnPrepare() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        order.markPaid();
        when(orders.findById(100L)).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.prepare(1L,
            new PaymentService.Prepare(100L, 25_000L, PaymentMethod.CARD)))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("PAY-P0-048 같은 paymentKey로 결제 승인을 재시도하면 PG를 다시 호출하지 않는다")
    void returnsExistingResultForSameConfirmation() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        Payment payment = approvedPayment(200L, 100L, 25_000L, "pay_same");
        order.markPaid();
        when(orders.findByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(payments.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));

        PaymentService.PaymentData result = service.confirm(1L,
            new PaymentService.Confirm("pay_same", order.getOrderNumber(), 25_000L));

        assertThat(result.status()).isEqualTo(PaymentStatus.DONE);
        verify(paymentGateway, never()).confirm(any(), any(), any(Long.class));
    }

    @Test
    @DisplayName("PAY-P0-043 준비된 결제를 승인하면 PG 승인 뒤 주문과 결제 상태가 함께 완료된다")
    void confirmsReadyPayment() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        Payment payment = payment(200L, 100L, 25_000L);
        when(orders.findByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(payments.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));

        PaymentService.PaymentData result = service.confirm(1L,
            new PaymentService.Confirm("pay_approved", order.getOrderNumber(), 25_000L));

        assertThat(result.status()).isEqualTo(PaymentStatus.DONE);
        assertThat(order.getStatus().name()).isEqualTo("PAID");
        verify(paymentGateway).confirm("pay_approved", order.getOrderNumber(), 25_000L);
    }

    @Test
    @DisplayName("PAY-P0-045 요청 orderId가 존재하지 않으면 422로 승인 차단한다")
    void rejectsMismatchedConfirmationOrderId() {
        when(orders.findByOrderNumberForUpdate("ORD-WRONG")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirm(1L,
            new PaymentService.Confirm("pay_key", "ORD-WRONG", 25_000L)))
            .isInstanceOf(BusinessRuleViolationException.class);
        verify(paymentGateway, never()).confirm(any(), any(), any(Long.class));
    }

    @Test
    @DisplayName("PAY-P0-046 DB 주문금액과 다른 승인 금액은 PG 호출 전에 차단한다")
    void rejectsTamperedConfirmationAmount() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        when(orders.findByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.confirm(1L,
            new PaymentService.Confirm("pay_key", order.getOrderNumber(), 1L)))
            .isInstanceOf(BusinessRuleViolationException.class);
        verify(paymentGateway, never()).confirm(any(), any(), any(Long.class));
    }

    @Test
    @DisplayName("PAY-P0-047 Toss가 명시적으로 승인을 거절하면 실패 상태와 사유를 저장하고 재고를 반환한다")
    void storesDefinitiveProviderRejection() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        Payment payment = payment(200L, 100L, 25_000L);
        when(orders.findByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(payments.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));
        doThrow(new PaymentProviderRejectedException("REJECT_CARD_COMPANY", "카드사 거절"))
            .when(paymentGateway).confirm("pay_rejected", order.getOrderNumber(), 25_000L);

        assertThatThrownBy(() -> service.confirm(1L,
            new PaymentService.Confirm("pay_rejected", order.getOrderNumber(), 25_000L)))
            .isInstanceOf(PaymentProviderRejectedException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("REJECT_CARD_COMPANY");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        verify(catalog).release(any());
    }

    @Test
    @DisplayName("PAY-P0-087 Toss 네트워크·5xx 오류는 결과가 불명확하므로 READY 상태를 유지한다")
    void keepsReadyStateForAmbiguousProviderFailure() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        Payment payment = payment(200L, 100L, 25_000L);
        when(orders.findByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(payments.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));
        doThrow(new ExternalServiceException("Toss unavailable")).when(paymentGateway)
            .confirm("pay_unknown", order.getOrderNumber(), 25_000L);

        assertThatThrownBy(() -> service.confirm(1L,
            new PaymentService.Confirm("pay_unknown", order.getOrderNumber(), 25_000L)))
            .isInstanceOf(ExternalServiceException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        verify(catalog, never()).release(any());
    }

    @Test
    @DisplayName("PAY-P0-049 다른 결제에서 승인된 paymentKey 재사용은 중복 처리로 차단한다")
    void rejectsPaymentKeyUsedByAnotherPayment() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        Payment payment = payment(200L, 100L, 25_000L);
        Payment other = approvedPayment(201L, 101L, 25_000L, "pay_used");
        when(orders.findByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(payments.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));
        when(payments.findByPaymentKey("pay_used")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.confirm(1L,
            new PaymentService.Confirm("pay_used", order.getOrderNumber(), 25_000L)))
            .isInstanceOf(ConcurrentUpdateException.class);
        verify(paymentGateway, never()).confirm(any(), any(), any(Long.class));
    }

    @Test
    @DisplayName("PAY-P0-050 다른 회원 주문 승인 시도는 404로 소유권을 감춘다")
    void hidesForeignOrderOnConfirm() {
        PurchaseOrder order = order(100L, 2L, 25_000L);
        when(orders.findByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));
        assertNotFound(() -> service.confirm(1L,
            new PaymentService.Confirm("pay_key", order.getOrderNumber(), 25_000L)));
    }

    @Test
    @DisplayName("PAY-P0-051/052/054 결제 실패는 사유를 저장하고 반복 처리에도 멱등이다")
    void failureIsIdempotent() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        Payment payment = payment(200L, 100L, 25_000L);
        when(orders.findByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(payments.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));
        when(paymentGateway.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.of(
            new PaymentGateway.PaymentSnapshot("pay_failed", order.getOrderNumber(), 25_000L, "ABORTED")));

        service.fail(1L, new PaymentService.Fail(order.getOrderNumber(), "PAY_PROCESS_CANCELED", "사용자 취소"));
        service.fail(1L, new PaymentService.Fail(order.getOrderNumber(), "PAY_PROCESS_CANCELED", "사용자 취소"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("PAY_PROCESS_CANCELED");
        assertThat(payment.getFailureMessage()).isEqualTo("사용자 취소");
        assertThat(order.getStatus().name()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    @DisplayName("PAY-P0-053 존재하지 않는 주문/결제 실패 처리는 404다")
    void rejectsUnknownFailureTarget() {
        when(orders.findByOrderNumberForUpdate("ORD-MISSING")).thenReturn(Optional.empty());
        assertNotFound(() -> service.fail(1L,
            new PaymentService.Fail("ORD-MISSING", "ERROR", "실패")));
    }

    @Test
    @DisplayName("PAY-P0-055 정상 결제 취소는 PG 호출 뒤 상태·재고·판매량을 함께 복원한다")
    void cancelsCompletedPayment() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        order.markPaid();
        Payment payment = approvedPayment(200L, 100L, 25_000L, "pay_done");
        when(payments.findById(200L)).thenReturn(Optional.of(payment));
        when(orders.findByIdForUpdate(100L)).thenReturn(Optional.of(order));
        when(payments.findByIdForUpdate(200L)).thenReturn(Optional.of(payment));

        PaymentService.PaymentData result = service.cancel(1L, 200L, "단순 변심");

        assertThat(result.status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        verify(paymentGateway).cancel("pay_done", "단순 변심");
        verify(catalog).release(any());
        verify(catalog).changeSalesCount(any(), org.mockito.ArgumentMatchers.eq(-1));
    }

    @Test
    @DisplayName("PAY-P0-056/057 없거나 다른 회원의 결제 취소는 404다")
    void hidesUnknownOrForeignPaymentOnCancel() {
        when(payments.findById(200L)).thenReturn(Optional.empty());
        assertNotFound(() -> service.cancel(1L, 200L, "취소"));

        Payment foreign = approvedPayment(201L, 101L, 25_000L, "pay_foreign");
        when(payments.findById(201L)).thenReturn(Optional.of(foreign));
        when(orders.findByIdForUpdate(101L)).thenReturn(Optional.of(order(101L, 2L, 25_000L)));
        assertNotFound(() -> service.cancel(1L, 201L, "취소"));
    }

    @Test
    @DisplayName("PAY-P0-058/059/061 이미 취소됐거나 취소 불가 상태인 요청은 중복 환불 없이 거부한다")
    void rejectsDuplicateOrInvalidCancellation() {
        PurchaseOrder canceledOrder = order(100L, 1L, 25_000L);
        canceledOrder.markPaid();
        canceledOrder.cancel();
        Payment canceled = approvedPayment(200L, 100L, 25_000L, "pay_done");
        canceled.cancel();
        when(payments.findById(200L)).thenReturn(Optional.of(canceled));
        when(orders.findByIdForUpdate(100L)).thenReturn(Optional.of(canceledOrder));
        when(payments.findByIdForUpdate(200L)).thenReturn(Optional.of(canceled));
        assertThatThrownBy(() -> service.cancel(1L, 200L, "중복"))
            .isInstanceOf(ConcurrentUpdateException.class);

        PurchaseOrder readyOrder = order(101L, 1L, 25_000L);
        Payment ready = payment(201L, 101L, 25_000L);
        when(payments.findById(201L)).thenReturn(Optional.of(ready));
        when(orders.findByIdForUpdate(101L)).thenReturn(Optional.of(readyOrder));
        when(payments.findByIdForUpdate(201L)).thenReturn(Optional.of(ready));
        assertThatThrownBy(() -> service.cancel(1L, 201L, "불가 상태"))
            .isInstanceOf(BusinessRuleViolationException.class);
        verify(paymentGateway, never()).cancel(any(), any());
    }

    @Test
    @DisplayName("PAY-P0-060 Toss 취소 API 실패 시 로컬 결제와 주문 상태는 유지된다")
    void keepsStateWhenProviderCancellationFails() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        order.markPaid();
        Payment payment = approvedPayment(200L, 100L, 25_000L, "pay_done");
        when(payments.findById(200L)).thenReturn(Optional.of(payment));
        when(orders.findByIdForUpdate(100L)).thenReturn(Optional.of(order));
        when(payments.findByIdForUpdate(200L)).thenReturn(Optional.of(payment));
        doThrow(new BusinessRuleViolationException("취소 실패")).when(paymentGateway).cancel("pay_done", "취소");

        assertThatThrownBy(() -> service.cancel(1L, 200L, "취소"))
            .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(catalog, never()).release(any());
    }

    @Test
    @DisplayName("브라우저 실패 콜백만 있고 토스 결제가 조회되지 않으면 주문 상태를 바꾸지 않는다")
    void ignoresUnverifiedClientFailure() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        Payment payment = payment(200L, 100L, 25_000L);
        when(orders.findByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(payments.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));
        when(paymentGateway.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.empty());

        service.fail(1L, new PaymentService.Fail(order.getOrderNumber(), "CLIENT_ERROR", "브라우저 오류"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(order.getStatus()).isEqualTo(com.jangingmall.backend.payment.domain.OrderStatus.CREATED);
        verify(catalog, never()).release(any());
    }

    @Test
    @DisplayName("PAY-P0-062/064 토스 웹훅은 서버 조회 결과가 일치할 때만 완료하고 중복 후처리를 하지 않는다")
    void verifiesAndHandlesDoneWebhookIdempotently() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        Payment payment = payment(200L, 100L, 25_000L);
        PaymentService.Webhook webhook = new PaymentService.Webhook(
            "PAYMENT_STATUS_CHANGED", "pay_webhook", order.getOrderNumber(), 25_000L, "DONE");
        when(paymentGateway.find("pay_webhook")).thenReturn(
            new PaymentGateway.PaymentSnapshot("pay_webhook", order.getOrderNumber(), 25_000L, "DONE"));
        when(orders.findByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(payments.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));

        service.handleWebhook(webhook);
        service.handleWebhook(webhook);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(order.getStatus()).isEqualTo(com.jangingmall.backend.payment.domain.OrderStatus.PAID);
        verify(catalog).changeSalesCount(any(), org.mockito.ArgumentMatchers.eq(1));
        verify(notifications).paymentCompleted(order);
    }

    @Test
    @DisplayName("PAY-P0-063 본문과 토스 서버 조회 결과가 다른 웹훅은 거부한다")
    void rejectsUnverifiedWebhook() {
        PaymentService.Webhook webhook = new PaymentService.Webhook(
            "PAYMENT_STATUS_CHANGED", "pay_webhook", "ORD-WRONG", 100L, "DONE");
        when(paymentGateway.find("pay_webhook")).thenReturn(
            new PaymentGateway.PaymentSnapshot("pay_webhook", "ORD-CORRECT", 100L, "DONE"));

        assertThatThrownBy(() -> service.handleWebhook(webhook)).isInstanceOf(ForbiddenException.class);
        verify(payments, never()).findByOrderIdForUpdate(any());
    }

    @Test
    @DisplayName("PAY-P0-067 READY 등 비최종 웹훅은 로컬 상태를 바꾸지 않는다")
    void ignoresNonFinalWebhookStatus() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        Payment payment = payment(200L, 100L, 25_000L);
        PaymentService.Webhook webhook = new PaymentService.Webhook(
            "PAYMENT_STATUS_CHANGED", "pay_ready", order.getOrderNumber(), 25_000L, "IN_PROGRESS");
        when(paymentGateway.find("pay_ready")).thenReturn(
            new PaymentGateway.PaymentSnapshot("pay_ready", order.getOrderNumber(), 25_000L, "IN_PROGRESS"));
        when(orders.findByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(payments.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));

        service.handleWebhook(webhook);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        verify(catalog, never()).release(any());
    }

    @Test
    @DisplayName("PAY-P0-068 지원하지 않는 웹훅 이벤트는 조회 없이 무시한다")
    void ignoresUnsupportedWebhookEvent() {
        service.handleWebhook(new PaymentService.Webhook(
            "DEPOSIT_CALLBACK", "pay_key", "ORD-000001", 25_000L, "DONE"));
        verify(paymentGateway, never()).find(any());
    }

    @Test
    @DisplayName("PAY-P0-065/066 최종 취소·실패 웹훅은 상태와 재고를 한 번만 정리한다")
    void handlesFinalFailureWebhook() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        Payment payment = payment(200L, 100L, 25_000L);
        PaymentService.Webhook webhook = new PaymentService.Webhook(
            "PAYMENT_STATUS_CHANGED", "pay_aborted", order.getOrderNumber(), 25_000L, "ABORTED");
        when(paymentGateway.find("pay_aborted")).thenReturn(
            new PaymentGateway.PaymentSnapshot("pay_aborted", order.getOrderNumber(), 25_000L, "ABORTED"));
        when(orders.findByOrderNumberForUpdate(order.getOrderNumber())).thenReturn(Optional.of(order));
        when(payments.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));

        service.handleWebhook(webhook);
        service.handleWebhook(webhook);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        verify(catalog).release(any());
    }

    @Test
    @DisplayName("PAY-P0-071 결제 제한 시간이 지난 주문은 실패 처리하고 선점 재고를 반환한다")
    void expiresAbandonedOrderAndReleasesInventory() {
        PurchaseOrder order = order(100L, 1L, 25_000L);
        Payment payment = payment(200L, 100L, 25_000L);
        Instant cutoff = Instant.now();
        when(orders.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            com.jangingmall.backend.payment.domain.OrderStatus.CREATED, cutoff)).thenReturn(List.of(order));
        when(payments.findByOrderIdForUpdate(100L)).thenReturn(Optional.of(payment));
        when(paymentGateway.findByOrderNumber(order.getOrderNumber())).thenReturn(Optional.empty());

        int processed = service.expireCreatedOrders(cutoff);

        assertThat(processed).isEqualTo(1);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(com.jangingmall.backend.payment.domain.OrderStatus.PAYMENT_FAILED);
        verify(catalog).release(any());
    }

    private CheckoutCartReader.CartLine cartLine() {
        return new CheckoutCartReader.CartLine(11L, 7L, 2, List.of(), List.of());
    }

    private CheckoutCatalog.ProductQuote quote() {
        return new CheckoutCatalog.ProductQuote(7L, "청자 다완", 12_500L, 14, 3L, 3_000L, 50_000L);
    }

    private PurchaseOrder.ShippingAddress address() {
        return new PurchaseOrder.ShippingAddress(9L, "홍길동", "01012345678", "03187", "서울시", "101호");
    }

    private PurchaseOrder order(Long id, Long memberId, long amount) {
        PurchaseOrder order = new PurchaseOrder("ORD-20260909-000001", memberId, address(), null, null,
            List.of(new PurchaseOrder.OrderLine(7L, "청자 다완", amount, 1, 14, "{}")));
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private Payment payment(Long id, Long orderId, long amount) {
        Payment payment = new Payment(orderId, amount, PaymentMethod.CARD);
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }

    private Payment approvedPayment(Long id, Long orderId, long amount, String paymentKey) {
        Payment payment = payment(id, orderId, amount);
        payment.approve(paymentKey);
        return payment;
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOf(DomainException.class)
            .extracting(error -> ((DomainException) error).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
    }
}
