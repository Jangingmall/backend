package com.jangingmall.backend.payment.infrastructure;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.payment.application.DeliveryTrackingGateway;
import com.jangingmall.backend.payment.application.SweetTrackerProperties;
import com.jangingmall.backend.payment.domain.DeliveryStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class SweetTrackerDeliveryTrackingGateway implements DeliveryTrackingGateway {

    private final SweetTrackerProperties properties;

    @Override
    @SuppressWarnings("unchecked")
    public TrackingSnapshot track(String carrierCode, String trackingNumber) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessRuleViolationException("스마트택배 API 키가 설정되지 않았습니다.");
        }
        try {
            Map<String, Object> response = RestClient.create(properties.getBaseUrl()).get()
                .uri(builder -> builder.path("/api/v1/trackingInfo")
                    .queryParam("t_key", properties.getApiKey())
                    .queryParam("t_code", carrierCode)
                    .queryParam("t_invoice", trackingNumber)
                    .build())
                .retrieve()
                .body(Map.class);
            if (response == null || Boolean.FALSE.equals(response.get("status"))) {
                throw new BusinessRuleViolationException("배송 정보를 조회할 수 없습니다.");
            }
            boolean completed = Boolean.TRUE.equals(response.get("complete")) || "Y".equals(response.get("completeYN"));
            DeliveryStatus status = completed ? DeliveryStatus.DELIVERED : DeliveryStatus.IN_TRANSIT;
            return new TrackingSnapshot(status, history(response, status));
        } catch (RestClientException exception) {
            throw new BusinessRuleViolationException("배송 정보를 조회할 수 없습니다.");
        }
    }

    @SuppressWarnings("unchecked")
    private List<TrackingEvent> history(Map<String, Object> response, DeliveryStatus finalStatus) {
        Object value = response.get("trackingDetails");
        if (!(value instanceof List<?> details)) {
            return List.of();
        }
        List<TrackingEvent> result = new ArrayList<>();
        for (int index = 0; index < details.size(); index++) {
            if (!(details.get(index) instanceof Map<?, ?> row)) {
                continue;
            }
            String occurredAt = text(row, "timeString", "time", "timeDate");
            String location = text(row, "where", "location", "telno");
            String description = text(row, "kind", "details", "description");
            DeliveryStatus eventStatus = index == details.size() - 1 ? finalStatus : DeliveryStatus.IN_TRANSIT;
            result.add(new TrackingEvent(occurredAt, location, description, eventStatus));
        }
        return List.copyOf(result);
    }

    private String text(Map<?, ?> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
