package com.jangingmall.backend.payment.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "delivery.sweet-tracker")
@Getter
@Setter
public class SweetTrackerProperties {
    private String apiKey = "";
    private String baseUrl = "https://info.sweettracker.co.kr";
}
