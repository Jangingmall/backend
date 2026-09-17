package com.jangingmall.backend.content.presentation;

import com.jangingmall.backend.content.application.GenerationCommand;
import com.jangingmall.backend.content.application.GenerationResponse;
import com.jangingmall.backend.content.application.GenerationService;
import com.jangingmall.backend.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/generations")
@RequiredArgsConstructor
public class AiCallbackController {

    private final GenerationService generationService;

    @PostMapping("/{generationId}/complete")
    public ApiResponse<GenerationResponse> complete(
        @PathVariable Long generationId,
        @Valid @RequestBody AiCallbackRequest.Complete request
    ) {
        GenerationCommand.Complete command = new GenerationCommand.Complete(generationId, request.reactDocument().toString());
        return ApiResponse.ok(generationService.complete(command));
    }
}
