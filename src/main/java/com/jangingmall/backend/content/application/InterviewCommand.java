package com.jangingmall.backend.content.application;

public sealed interface InterviewCommand {

    record Create(
        Long productId,
        Long requesterId,
        String process,
        String materials,
        String technique,
        String story
    ) implements InterviewCommand {}

    record Update(
        Long productId,
        Long requesterId,
        String process,
        String materials,
        String technique,
        String story
    ) implements InterviewCommand {}
}
