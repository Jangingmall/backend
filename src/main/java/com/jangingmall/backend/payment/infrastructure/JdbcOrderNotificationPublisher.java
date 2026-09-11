package com.jangingmall.backend.payment.infrastructure;

import com.jangingmall.backend.notification.domain.Notification;
import com.jangingmall.backend.notification.infrastructure.NotificationRepository;
import com.jangingmall.backend.payment.application.OrderNotificationPublisher;
import com.jangingmall.backend.payment.domain.OrderReturn;
import com.jangingmall.backend.payment.domain.PurchaseOrder;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JdbcOrderNotificationPublisher implements OrderNotificationPublisher {

    private final JdbcTemplate jdbcTemplate;
    private final NotificationRepository notifications;

    @Override
    public void paymentCompleted(PurchaseOrder order) {
        notifications.save(Notification.create(order.getMemberId(), "결제가 완료되었습니다.",
            "주문 " + order.getOrderNumber() + "의 결제가 완료되었습니다."));
        saveForSellers(order, "새 주문이 접수되었습니다.",
            "주문 " + order.getOrderNumber() + "의 결제가 완료되어 제작·발송 준비가 필요합니다.", false);
    }

    @Override
    public void returnRequested(PurchaseOrder order, OrderReturn orderReturn) {
        saveForSellers(order, "교환·반품 요청이 접수되었습니다.",
            "주문 " + order.getOrderNumber() + "에 " + orderReturn.getType() + " 요청이 접수되었습니다.", true);
    }

    private void saveForSellers(PurchaseOrder order, String title, String content, boolean includeAdmins) {
        Set<Long> recipients = new LinkedHashSet<>();
        List<Long> productIds = order.getItems().stream().map(item -> item.getProductId()).distinct().toList();
        if (!productIds.isEmpty()) {
            String placeholders = String.join(",", java.util.Collections.nCopies(productIds.size(), "?"));
            recipients.addAll(jdbcTemplate.queryForList(
                "select distinct artisan_id from product where product_id in (" + placeholders + ")",
                Long.class,
                productIds.toArray()
            ));
        }
        if (includeAdmins) {
            recipients.addAll(jdbcTemplate.queryForList(
                "select member_id from member where role = 'ADMIN' and status = 'ACTIVE'",
                Long.class
            ));
        }
        notifications.saveAll(recipients.stream().map(memberId -> Notification.create(memberId, title, content)).toList());
    }
}
