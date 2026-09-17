package com.jangingmall.backend.payment.application;

import com.jangingmall.backend.payment.domain.PurchaseOrder;

public interface ShippingAddressReader {
    PurchaseOrder.ShippingAddress findOwned(Long memberId, Long addressId);
}
