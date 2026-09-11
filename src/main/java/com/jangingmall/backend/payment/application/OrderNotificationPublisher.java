package com.jangingmall.backend.payment.application;

import com.jangingmall.backend.payment.domain.OrderReturn;
import com.jangingmall.backend.payment.domain.PurchaseOrder;

public interface OrderNotificationPublisher {
    void paymentCompleted(PurchaseOrder order);
    void returnRequested(PurchaseOrder order, OrderReturn orderReturn);
}
