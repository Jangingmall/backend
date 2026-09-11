package com.jangingmall.backend.payment.application;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ConcurrentUpdateException;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.global.exception.ForbiddenException;
import com.jangingmall.backend.global.exception.PaymentProviderRejectedException;
import com.jangingmall.backend.member.application.MemberAccess;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.payment.domain.OrderItem;
import com.jangingmall.backend.payment.domain.OrderStatus;
import com.jangingmall.backend.payment.domain.Payment;
import com.jangingmall.backend.payment.domain.PaymentMethod;
import com.jangingmall.backend.payment.domain.PaymentRepository;
import com.jangingmall.backend.payment.domain.PaymentStatus;
import com.jangingmall.backend.payment.domain.PurchaseOrder;
import com.jangingmall.backend.payment.domain.PurchaseOrderRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberAccess memberAccess;
    private final ShippingAddressReader shippingAddresses;
    private final CheckoutCartReader checkoutCart;
    private final CheckoutCatalog catalog;
    private final PurchaseOrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentGateway paymentGateway;
    private final PaymentProperties properties;
    private final OrderNotificationPublisher notifications;

    @Transactional
    public OrderData createOrder(Long memberId, CreateOrder command, String idempotencyKey) {
        memberAccess.requireRole(memberId, MemberRole.USER);
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        catalog.lockOrderCreation(memberId, normalizedKey);
        if (normalizedKey != null) {
            PurchaseOrder existing = orders.findByMemberIdAndClientRequestKey(memberId, normalizedKey).orElse(null);
            if (existing != null) {
                return OrderData.from(existing);
            }
        }
        if (command.paymentMethod() == null) {
            throw new BusinessRuleViolationException("결제수단을 선택해야 합니다.");
        }
        PurchaseOrder.ShippingAddress address = shippingAddresses.findOwned(memberId, command.addressId());
        List<CheckoutCartReader.CartLine> cartLines = checkoutCart.selectedItems(memberId, command.cartItemIds());
        List<QuotedLine> quotedLines = cartLines.stream().map(this::quote).toList();
        List<PurchaseOrder.OrderLine> lines = quotedLines.stream().map(this::toOrderLine).toList();
        long shippingAmount = shippingAmount(quotedLines);
        catalog.reserve(cartLines.stream().map(this::inventoryLine).toList());
        PurchaseOrder order = new PurchaseOrder(nextOrderNumber(), memberId, address, command.deliveryRequest(),
            command.paymentMethod(), normalizedKey, lines, shippingAmount);
        return OrderData.from(orders.save(order));
    }

    @Transactional
    public PreparedPayment prepare(Long memberId, Prepare command) {
        memberAccess.requireRole(memberId, MemberRole.USER);
        if (properties.getClientKey() == null || properties.getClientKey().isBlank()) {
            throw new BusinessRuleViolationException("토스페이먼츠 클라이언트 키가 설정되지 않았습니다.");
        }
        PurchaseOrder order = ownedOrder(memberId, command.orderId());
        if (order.getStatus() != OrderStatus.CREATED || order.getTotalAmount() != command.amount()) {
            throw new BusinessRuleViolationException("결제 가능한 주문 또는 금액이 아닙니다.");
        }
        PaymentMethod paymentMethod = command.method() == null ? order.getPaymentMethod() : command.method();
        if (paymentMethod != order.getPaymentMethod()) {
            throw new BusinessRuleViolationException("주문 시 선택한 결제수단과 일치하지 않습니다.");
        }
        Payment existing = payments.findByOrderId(order.getId()).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == PaymentStatus.READY) {
                return PreparedPayment.from(existing, properties.getClientKey(), false);
            }
            throw new BusinessRuleViolationException("이미 결제 처리가 시작된 주문입니다.");
        }
        Payment payment = payments.save(new Payment(order.getId(), order.getTotalAmount(), paymentMethod));
        return PreparedPayment.from(payment, properties.getClientKey(), true);
    }

    @Transactional(noRollbackFor = PaymentProviderRejectedException.class)
    public PaymentData confirm(Long memberId, Confirm command) {
        memberAccess.requireRole(memberId, MemberRole.USER);
        PurchaseOrder order = orders.findByOrderNumberForUpdate(command.orderNumber())
            .orElseThrow(() -> new BusinessRuleViolationException("요청 orderId가 주문과 일치하지 않습니다."));
        if (!order.getMemberId().equals(memberId)) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        if (order.getTotalAmount() != command.amount()) {
            throw new BusinessRuleViolationException("요청 금액이 주문 금액과 일치하지 않습니다.");
        }
        Payment payment = payments.findByOrderIdForUpdate(order.getId())
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (payment.isSameConfirmation(command.paymentKey()) && order.getStatus() == OrderStatus.PAID) {
            return PaymentData.from(payment, order);
        }
        if (payment.getAmount() != command.amount()
            || payment.getStatus() != PaymentStatus.READY || order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessRuleViolationException("결제 승인 가능한 상태가 아닙니다.");
        }
        payments.findByPaymentKey(command.paymentKey())
            .filter(found -> !found.getId().equals(payment.getId()))
            .ifPresent(found -> { throw new ConcurrentUpdateException("이미 처리된 paymentKey입니다."); });
        try {
            paymentGateway.confirm(command.paymentKey(), order.getOrderNumber(), command.amount());
        } catch (PaymentProviderRejectedException exception) {
            payment.fail(exception.providerCode(), exception.getMessage());
            order.markPaymentFailed();
            catalog.release(inventoryLines(order));
            throw exception;
        }
        completePayment(payment, order, command.paymentKey());
        return PaymentData.from(payment, order);
    }

    @Transactional
    public void fail(Long memberId, Fail command) {
        memberAccess.requireRole(memberId, MemberRole.USER);
        PurchaseOrder order = ownedOrderNumberForUpdate(memberId, command.orderNumber());
        Payment payment = payments.findByOrderIdForUpdate(order.getId())
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (payment.getStatus() == PaymentStatus.FAILED && order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.READY || order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessRuleViolationException("결제 실패 처리 가능한 상태가 아닙니다.");
        }
        Optional<PaymentGateway.PaymentSnapshot> verified = paymentGateway.findByOrderNumber(order.getOrderNumber());
        if (verified.isEmpty()) {
            // 브라우저 실패 리다이렉트는 신뢰하지 않는다. 미생성 결제는 만료 스케줄러가 최종 정리한다.
            return;
        }
        PaymentGateway.PaymentSnapshot snapshot = verified.get();
        verifyProviderOrder(order, payment, snapshot);
        if ("DONE".equals(snapshot.status())) {
            completePayment(payment, order, snapshot.paymentKey());
            return;
        }
        if (isProviderFailure(snapshot.status())) {
            payment.fail(command.errorCode(), command.errorMessage());
            order.markPaymentFailed();
            catalog.release(inventoryLines(order));
        }
    }

    @Transactional
    public PaymentData cancel(Long memberId, Long paymentId, String reason) {
        memberAccess.active(memberId);
        Payment candidate = payments.findById(paymentId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        PurchaseOrder order = orders.findByIdForUpdate(candidate.getOrderId())
            .filter(foundOrder -> foundOrder.getMemberId().equals(memberId))
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        Payment payment = payments.findByIdForUpdate(paymentId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (payment.getStatus() == PaymentStatus.CANCELED || order.getStatus() == OrderStatus.CANCELED) {
            throw new ConcurrentUpdateException("이미 취소된 결제입니다.");
        }
        if (payment.getStatus() != PaymentStatus.DONE || order.getStatus() != OrderStatus.PAID || payment.getPaymentKey() == null) {
            throw new BusinessRuleViolationException("결제 완료 건만 취소할 수 있습니다.");
        }
        paymentGateway.cancel(payment.getPaymentKey(), reason);
        payment.cancel();
        order.cancel();
        List<CheckoutCatalog.InventoryLine> inventory = inventoryLines(order);
        catalog.release(inventory);
        catalog.changeSalesCount(inventory, -1);
        return PaymentData.from(payment, order);
    }

    /**
     * 일반 결제 웹훅에는 신뢰할 수 있는 서명 헤더가 없으므로, 본문을 처리하기 전에 토스 조회 API 결과와 대조한다.
     */
    @Transactional
    public void handleWebhook(Webhook command) {
        if (!"PAYMENT_STATUS_CHANGED".equals(command.eventType())) {
            return;
        }
        PaymentGateway.PaymentSnapshot verified = paymentGateway.find(command.paymentKey());
        if (!command.paymentKey().equals(verified.paymentKey())
            || !command.orderNumber().equals(verified.orderNumber())
            || command.totalAmount() != verified.totalAmount()
            || !command.status().equals(verified.status())) {
            throw new ForbiddenException("검증되지 않은 결제 웹훅입니다.");
        }

        PurchaseOrder order = orders.findByOrderNumberForUpdate(verified.orderNumber())
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        Payment payment = payments.findByOrderIdForUpdate(order.getId())
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        verifyProviderOrder(order, payment, verified);

        switch (verified.status()) {
            case "DONE" -> {
                if (payment.isSameConfirmation(verified.paymentKey()) && order.getStatus() == OrderStatus.PAID) {
                    return;
                }
                if (payment.getStatus() != PaymentStatus.READY || order.getStatus() != OrderStatus.CREATED) {
                    throw new ConcurrentUpdateException("이미 다른 상태로 처리된 결제입니다.");
                }
                completePayment(payment, order, verified.paymentKey());
            }
            case "CANCELED" -> cancelFromWebhook(payment, order);
            case "ABORTED", "EXPIRED" -> failFromWebhook(payment, order, verified.status());
            default -> {
                // READY, IN_PROGRESS, WAITING_FOR_DEPOSIT 등은 최종 상태가 아니므로 로컬 상태를 변경하지 않는다.
            }
        }
    }

    /**
     * 결제창을 닫고 돌아오지 않은 주문을 만료시켜 선점된 재고를 반환한다.
     * 주문 행을 먼저 잠그므로 승인·웹훅 처리와 동시에 실행되어도 한 쪽만 최종 상태를 변경한다.
     */
    @Transactional
    public int expireCreatedOrders(Instant cutoff) {
        List<PurchaseOrder> expired = orders.findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            OrderStatus.CREATED, cutoff);
        int processed = 0;
        for (PurchaseOrder order : expired) {
            Payment payment = payments.findByOrderIdForUpdate(order.getId()).orElse(null);
            if (payment != null && (payment.getStatus() == PaymentStatus.DONE || payment.getStatus() == PaymentStatus.CANCELED)) {
                continue;
            }
            if (payment != null && payment.getStatus() == PaymentStatus.READY) {
                Optional<PaymentGateway.PaymentSnapshot> verified = paymentGateway.findByOrderNumber(order.getOrderNumber());
                if (verified.isPresent()) {
                    PaymentGateway.PaymentSnapshot snapshot = verified.get();
                    verifyProviderOrder(order, payment, snapshot);
                    if ("DONE".equals(snapshot.status())) {
                        completePayment(payment, order, snapshot.paymentKey());
                        processed++;
                        continue;
                    }
                    if (!isProviderFailure(snapshot.status())) {
                        continue;
                    }
                    payment.fail("TOSS_" + snapshot.status(), "토스페이먼츠 결제가 최종 실패 상태입니다.");
                } else {
                    payment.fail("ORDER_EXPIRED", "결제 제한 시간이 지나 주문이 만료되었습니다.");
                }
            }
            order.markPaymentFailed();
            catalog.release(inventoryLines(order));
            processed++;
        }
        return processed;
    }

    private QuotedLine quote(CheckoutCartReader.CartLine item) {
        CheckoutCatalog.ProductQuote quote = catalog.quote(item.productId(), item.quantity(), item.selectedOptions(), item.textInputs());
        return new QuotedLine(item, quote);
    }

    private PurchaseOrder.OrderLine toOrderLine(QuotedLine quoted) {
        CheckoutCartReader.CartLine item = quoted.cartLine();
        CheckoutCatalog.ProductQuote quote = quoted.quote();
        return new PurchaseOrder.OrderLine(quote.productId(), quote.productName(), quote.unitPrice(),
            item.quantity(), quote.productionPeriodDays(),
            CheckoutSnapshot.encode(item.cartItemId(), item.selectedOptions(), item.textInputs()));
    }

    private long shippingAmount(List<QuotedLine> lines) {
        Map<Long, List<QuotedLine>> byArtisan = lines.stream().collect(Collectors.groupingBy(
            line -> line.quote().artisanId()));
        long total = 0L;
        for (List<QuotedLine> artisanLines : byArtisan.values()) {
            long subtotal = artisanLines.stream().map(line -> Math.multiplyExact(
                line.quote().unitPrice(), line.cartLine().quantity())).reduce(0L, Math::addExact);
            long fee = artisanLines.stream().mapToLong(line -> line.quote().shippingFee()).max().orElse(0L);
            Long threshold = artisanLines.stream().map(line -> line.quote().freeShippingThreshold())
                .filter(java.util.Objects::nonNull).max(Long::compareTo).orElse(null);
            if (threshold == null || subtotal < threshold) {
                total = Math.addExact(total, fee);
            }
        }
        return total;
    }

    private CheckoutCatalog.InventoryLine inventoryLine(CheckoutCartReader.CartLine item) {
        return new CheckoutCatalog.InventoryLine(item.productId(), item.quantity(),
            item.selectedOptions().stream().map(CheckoutCatalog.OptionSelection::choiceId).toList());
    }

    private List<CheckoutCatalog.InventoryLine> inventoryLines(PurchaseOrder order) {
        return order.getItems().stream()
            .map(item -> new CheckoutCatalog.InventoryLine(item.getProductId(), item.getQuantity(),
                CheckoutSnapshot.choiceIds(item.getSelectedOptionsSnapshot())))
            .toList();
    }

    private List<Long> cartItemIds(PurchaseOrder order) {
        return order.getItems().stream()
            .map(item -> CheckoutSnapshot.cartItemId(item.getSelectedOptionsSnapshot()))
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    private void completePayment(Payment payment, PurchaseOrder order, String paymentKey) {
        payment.approve(paymentKey);
        order.markPaid();
        catalog.changeSalesCount(inventoryLines(order), 1);
        checkoutCart.removePurchased(order.getMemberId(), cartItemIds(order));
        notifications.paymentCompleted(order);
    }

    private void cancelFromWebhook(Payment payment, PurchaseOrder order) {
        if (payment.getStatus() == PaymentStatus.CANCELED && order.getStatus() == OrderStatus.CANCELED) {
            return;
        }
        if (payment.getStatus() == PaymentStatus.READY && order.getStatus() == OrderStatus.CREATED) {
            payment.fail("TOSS_CANCELED", "토스페이먼츠에서 결제가 취소되었습니다.");
            order.markPaymentFailed();
            catalog.release(inventoryLines(order));
            return;
        }
        if (payment.getStatus() != PaymentStatus.DONE || order.getStatus() != OrderStatus.PAID) {
            throw new ConcurrentUpdateException("결제 취소 상태가 일치하지 않습니다.");
        }
        payment.cancel();
        order.cancel();
        List<CheckoutCatalog.InventoryLine> inventory = inventoryLines(order);
        catalog.release(inventory);
        catalog.changeSalesCount(inventory, -1);
    }

    private void failFromWebhook(Payment payment, PurchaseOrder order, String status) {
        if (payment.getStatus() == PaymentStatus.FAILED && order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.READY || order.getStatus() != OrderStatus.CREATED) {
            throw new ConcurrentUpdateException("결제 실패 상태가 일치하지 않습니다.");
        }
        payment.fail("TOSS_" + status, "토스페이먼츠 결제가 " + status + " 상태로 종료되었습니다.");
        order.markPaymentFailed();
        catalog.release(inventoryLines(order));
    }

    private void verifyProviderOrder(PurchaseOrder order, Payment payment, PaymentGateway.PaymentSnapshot snapshot) {
        if (!order.getOrderNumber().equals(snapshot.orderNumber())
            || order.getTotalAmount() != snapshot.totalAmount()
            || payment.getAmount() != snapshot.totalAmount()) {
            throw new ForbiddenException("결제 금액 또는 주문번호가 주문과 일치하지 않습니다.");
        }
    }

    private boolean isProviderFailure(String status) {
        return "CANCELED".equals(status) || "ABORTED".equals(status) || "EXPIRED".equals(status);
    }

    private PurchaseOrder ownedOrder(Long memberId, Long orderId) {
        return orders.findById(orderId)
            .filter(order -> order.getMemberId().equals(memberId))
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private PurchaseOrder ownedOrderNumberForUpdate(Long memberId, String orderNumber) {
        return orders.findByOrderNumberForUpdate(orderNumber)
            .filter(order -> order.getMemberId().equals(memberId))
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 100) {
            throw new BusinessRuleViolationException("Idempotency-Key는 100자 이하여야 합니다.");
        }
        return normalized;
    }

    private String nextOrderNumber() {
        return "ORD-" + Instant.now().toEpochMilli() + "-" + String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    public record CreateOrder(List<Long> cartItemIds, Long addressId, String deliveryRequest, PaymentMethod paymentMethod) {}

    public record Prepare(Long orderId, long amount, PaymentMethod method) {}

    public record Confirm(String paymentKey, String orderNumber, long amount) {}

    public record Fail(String orderNumber, String errorCode, String errorMessage) {}

    public record Webhook(String eventType, String paymentKey, String orderNumber, long totalAmount, String status) {}

    private record QuotedLine(CheckoutCartReader.CartLine cartLine, CheckoutCatalog.ProductQuote quote) {}

    public record OrderData(Long orderId, String orderNumber, long totalAmount, OrderStatus status, Instant createdAt,
                            List<OrderItemData> items) {
        static OrderData from(PurchaseOrder order) {
            return new OrderData(order.getId(), order.getOrderNumber(), order.getTotalAmount(), order.getStatus(), order.getCreatedAt(),
                order.getItems().stream().map(OrderItemData::from).toList());
        }
    }

    public record OrderItemData(Long orderItemId, Long productId, String productName, long price, int quantity, long totalPrice) {
        static OrderItemData from(OrderItem item) {
            return new OrderItemData(item.getId(), item.getProductId(), item.getProductNameSnapshot(), item.getPriceSnapshot(),
                item.getQuantity(), item.getTotalPrice());
        }
    }

    public record PreparedPayment(Long paymentId, Long orderId, long amount, String tossClientKey, boolean created) {
        static PreparedPayment from(Payment payment, String clientKey, boolean created) {
            return new PreparedPayment(payment.getId(), payment.getOrderId(), payment.getAmount(), clientKey, created);
        }
    }

    public record PaymentData(Long paymentId, Long orderId, String orderNumber, long amount, PaymentMethod method, PaymentStatus status,
                              Instant approvedAt, Instant canceledAt) {
        static PaymentData from(Payment payment, PurchaseOrder order) {
            return new PaymentData(payment.getId(), order.getId(), order.getOrderNumber(), payment.getAmount(), payment.getMethod(), payment.getStatus(),
                payment.getApprovedAt(), payment.getCanceledAt());
        }
    }
}
