package com.jangingmall.backend.content.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public sealed interface InterviewRequest {

    record Create(
        @NotBlank String process,
        @NotBlank @Size(max = 255) String materials,
        @NotBlank @Size(max = 100) String technique,
        @NotBlank String story
    ) implements InterviewRequest {}

    record Update(
        String process,
        @Size(max = 255) String materials,
        @Size(max = 100) String technique,
        String story
    ) implements InterviewRequest {}
}
