package com.jangingmall.backend.payment.application;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.application.MemberAccess;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.image.application.ImageService;
import com.jangingmall.backend.image.domain.ImagePurpose;
import com.jangingmall.backend.payment.domain.OrderReturn;
import com.jangingmall.backend.payment.domain.OrderReturnRepository;
import com.jangingmall.backend.payment.domain.OrderStatus;
import com.jangingmall.backend.payment.domain.PurchaseOrder;
import com.jangingmall.backend.payment.domain.PurchaseOrderRepository;
import com.jangingmall.backend.payment.domain.ReturnReason;
import com.jangingmall.backend.payment.domain.ReturnStatus;
import com.jangingmall.backend.payment.domain.ReturnType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReturnService {

    private final MemberAccess memberAccess;
    private final ShippingAddressReader shippingAddresses;
    private final PurchaseOrderRepository orders;
    private final OrderReturnRepository returns;
    private final ImageService images;
    private final OrderNotificationPublisher notifications;

    @Transactional
    public ReturnData request(Long memberId, RequestReturn command) {
        memberAccess.requireRole(memberId, MemberRole.USER);
        validate(command);

        PurchaseOrder order = orders.findByIdForUpdate(command.orderId())
            .filter(candidate -> candidate.getMemberId().equals(memberId))
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessRuleViolationException("결제 완료 또는 배송 완료 주문만 반품 신청할 수 있습니다.");
        }
        Long returnAddressId = command.returnAddressId() == null ? order.getAddressId()
            : shippingAddresses.findOwned(memberId, command.returnAddressId()).addressId();
        if (returnAddressId == null) {
            throw new BusinessRuleViolationException("회수지 주소를 확인할 수 없습니다.");
        }
        if (returns.findByOrderId(order.getId()).isPresent()) {
            throw new DomainException(ErrorCode.CONFLICT);
        }

        Set<Long> ownedItemIds = order.getItems().stream().map(item -> item.getId()).collect(Collectors.toSet());
        if (!ownedItemIds.containsAll(command.orderItemIds())) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        List<String> imageIds = images.consumeOwned(memberId, ImagePurpose.RETURN, command.imageIds());

        OrderReturn orderReturn = returns.save(new OrderReturn(order.getId(), command.type(), command.reason(),
            normalize(command.description()), returnAddressId, longIds(command.orderItemIds()), stringIds(imageIds)));
        order.requestReturn();
        notifications.returnRequested(order, orderReturn);
        return ReturnData.from(orderReturn);
    }

    private void validate(RequestReturn command) {
        String description = normalize(command.description());
        List<Long> orderItemIds = command.orderItemIds() == null ? List.of() : command.orderItemIds();
        List<String> imageIds = command.imageIds() == null ? List.of() : command.imageIds();
        if (orderItemIds.isEmpty() || orderItemIds.stream().anyMatch(java.util.Objects::isNull)
            || orderItemIds.stream().distinct().count() != orderItemIds.size()) {
            throw new BusinessRuleViolationException("반품 대상 주문 상품을 중복 없이 한 개 이상 선택해야 합니다.");
        }
        if (command.reason() == ReturnReason.OTHER && description == null) {
            throw new BusinessRuleViolationException("기타 반품 사유는 상세 설명이 필요합니다.");
        }
        if (command.reason() == ReturnReason.DEFECTIVE && imageIds.isEmpty()) {
            throw new BusinessRuleViolationException("불량 반품은 사진을 한 장 이상 등록해야 합니다.");
        }
        if (imageIds.size() > 5 || imageIds.stream().anyMatch(key -> key == null || key.isBlank())
            || imageIds.stream().distinct().count() != imageIds.size()) {
            throw new BusinessRuleViolationException("반품 사진은 중복 없이 최대 5장까지 등록할 수 있습니다.");
        }
    }

    private String stringIds(List<String> values) {
        List<String> normalized = values == null ? List.of() : values.stream().map(String::trim).toList();
        return normalized.stream()
            .map(key -> "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
            .reduce("[", (json, key) -> json.equals("[") ? json + key : json + "," + key) + "]";
    }

    private String longIds(List<Long> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record RequestReturn(Long orderId, ReturnType type, List<Long> orderItemIds, ReturnReason reason,
                                String description, List<String> imageIds, Long returnAddressId) {}

    public record ReturnData(Long returnId, Long orderId, ReturnType type, ReturnStatus status, Instant requestedAt) {
        static ReturnData from(OrderReturn orderReturn) {
            return new ReturnData(orderReturn.getId(), orderReturn.getOrderId(), orderReturn.getType(),
                orderReturn.getStatus(), orderReturn.getCreatedAt());
        }
    }
}
