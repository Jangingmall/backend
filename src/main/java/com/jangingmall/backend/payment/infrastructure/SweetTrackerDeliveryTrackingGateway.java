package com.jangingmall.backend.payment.infrastructure;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.payment.application.DeliveryTrackingGateway;
import com.jangingmall.backend.payment.application.SweetTrackerProperties;
import com.jangingmall.backend.payment.domain.DeliveryStatus;
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
            return new TrackingSnapshot(completed ? DeliveryStatus.DELIVERED : DeliveryStatus.IN_TRANSIT);
        } catch (RestClientException exception) {
            throw new BusinessRuleViolationException("배송 정보를 조회할 수 없습니다.");
        }
    }
}
