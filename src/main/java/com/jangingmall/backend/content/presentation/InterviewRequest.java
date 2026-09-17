package com.jangingmall.backend.content.presentation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public sealed interface InterviewRequest {

    record Create(
        @NotBlank(message = "제작 과정은 필수입니다") String process,
        @NotBlank(message = "재료는 필수입니다") @Size(max = 255, message = "재료는 255자 이내여야 합니다") String materials,
        @NotBlank(message = "기법은 필수입니다") @Size(max = 100, message = "기법은 100자 이내여야 합니다") String technique,
        @NotBlank(message = "작품 스토리는 필수입니다") String story
    ) implements InterviewRequest {}

    record Update(
        String process,
        @Size(max = 255, message = "재료는 255자 이내여야 합니다") String materials,
        @Size(max = 100, message = "기법은 100자 이내여야 합니다") String technique,
        String story
    ) implements InterviewRequest {}
}
