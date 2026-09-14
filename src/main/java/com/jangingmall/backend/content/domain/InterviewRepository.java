package com.jangingmall.backend.content.domain;

import java.util.Optional;

public interface InterviewRepository {

    Interview save(Interview interview);

    Optional<Interview> findByProductId(Long productId);

    boolean existsByProductId(Long productId);
}
