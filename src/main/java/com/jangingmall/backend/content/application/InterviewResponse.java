package com.jangingmall.backend.content.application;

import com.jangingmall.backend.content.domain.Interview;

public record InterviewResponse(
    Long productId,
    String process,
    String materials,
    String technique,
    String story
) {
    public static InterviewResponse from(Interview interview) {
        return new InterviewResponse(
            interview.getProductId(),
            interview.getProcess(),
            interview.getMaterials(),
            interview.getTechnique(),
            interview.getStory()
        );
    }
}
