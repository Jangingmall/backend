package com.jangingmall.backend.payment.application;

import com.jangingmall.backend.payment.domain.DeliveryStatus;

public interface DeliveryTrackingGateway {
    TrackingSnapshot track(String carrierCode, String trackingNumber);

    record TrackingSnapshot(DeliveryStatus status) {}
}
