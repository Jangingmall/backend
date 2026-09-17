package com.jangingmall.backend.content.application;

public record GenerationRequestedEvent(
    Long generationId,
    GenerationCommand.Request command
) {}
