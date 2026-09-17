package com.jangingmall.backend.payment.application;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.application.MemberAccess;
import com.jangingmall.backend.payment.domain.DeliveryStatus;
import com.jangingmall.backend.payment.domain.OrderDelivery;
import com.jangingmall.backend.payment.domain.OrderDeliveryRepository;
import com.jangingmall.backend.payment.domain.OrderStatus;
import com.jangingmall.backend.payment.domain.PurchaseOrder;
import com.jangingmall.backend.payment.domain.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final MemberAccess memberAccess;
    private final PurchaseOrderRepository orders;
    private final OrderDeliveryRepository deliveries;
    private final DeliveryTrackingGateway trackingGateway;

    @Transactional
    public DeliveryData get(Long memberId, Long orderId) {
        memberAccess.active(memberId);
        PurchaseOrder order = orders.findByIdForUpdate(orderId)
            .filter(candidate -> candidate.getMemberId().equals(memberId))
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (order.getStatus() == OrderStatus.CREATED || order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        OrderDelivery delivery = deliveries.findByOrderId(orderId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        DeliveryStatus currentStatus = trackingGateway.track(delivery.getCarrierCode(), delivery.getTrackingNumber()).status();
        delivery.updateStatus(currentStatus);
        if (currentStatus == DeliveryStatus.DELIVERED && order.getStatus() == OrderStatus.PAID) {
            order.markDelivered();
        }
        return DeliveryData.from(delivery);
    }

    /**
     * 장인 주문 처리 기능에서 발송 확정 시 호출한다. 고객 API에는 노출하지 않는다.
     */
    @Transactional
    public void registerDispatch(Long orderId, String carrierCode, String carrierName, String trackingNumber) {
        PurchaseOrder order = orders.findById(orderId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessRuleViolationException("결제 완료 주문만 배송 정보를 등록할 수 있습니다.");
        }
        if (deliveries.findByOrderId(orderId).isPresent()) {
            throw new DomainException(ErrorCode.CONFLICT);
        }
        deliveries.save(new OrderDelivery(orderId, carrierCode.trim(), carrierName.trim(), trackingNumber.trim()));
    }

    public record DeliveryData(Long orderId, String carrier, String trackingNumber, DeliveryStatus status) {
        static DeliveryData from(OrderDelivery delivery) {
            return new DeliveryData(delivery.getOrderId(), delivery.getCarrierName(), delivery.getTrackingNumber(),
                delivery.getStatus());
        }
    }
}
