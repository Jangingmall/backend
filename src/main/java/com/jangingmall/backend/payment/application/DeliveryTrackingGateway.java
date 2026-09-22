package com.jangingmall.backend.payment.application;

import com.jangingmall.backend.payment.domain.DeliveryStatus;
import java.util.List;

public interface DeliveryTrackingGateway {
    TrackingSnapshot track(String carrierCode, String trackingNumber);

    record TrackingSnapshot(DeliveryStatus status, List<TrackingEvent> history) {
        public TrackingSnapshot(DeliveryStatus status) {
            this(status, List.of());
        }

        public TrackingSnapshot {
            history = history == null ? List.of() : List.copyOf(history);
        }
    }

    /** A provider-neutral movement record. occurredAt remains text because carrier APIs use inconsistent formats. */
    record TrackingEvent(String occurredAt, String location, String description, DeliveryStatus status) {}
}
