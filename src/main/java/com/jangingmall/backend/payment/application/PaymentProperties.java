package com.jangingmall.backend.payment.application;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "toss.payments")
@Validated
@Getter
@Setter
public class PaymentProperties {
    private String secretKey = "";
    private String clientKey = "";
    private String baseUrl = "https://api.tosspayments.com";
    @Min(60)
    private long orderExpirationSeconds = 1_800;
}
