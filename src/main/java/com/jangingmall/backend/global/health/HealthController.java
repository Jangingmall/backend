package com.jangingmall.backend.global.health;

import com.jangingmall.backend.global.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping({"/api/health", "/healthz"})
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.ok(new HealthResponse("ok"));
    }

    public record HealthResponse(String status) {}
}
