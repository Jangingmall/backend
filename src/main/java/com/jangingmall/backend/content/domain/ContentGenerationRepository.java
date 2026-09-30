package com.jangingmall.backend.content.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ContentGenerationRepository {
    ContentGeneration save(ContentGeneration generation);
    Optional<ContentGeneration> findById(Long id);

    /** 행 잠금(SELECT ... FOR UPDATE)으로 조회한다. 같은 건을 동시에 갱신하는 콜백을 직렬화할 때 사용한다. */
    Optional<ContentGeneration> findByIdForUpdate(Long id);

    /**
     * DRAFT_READY 건의 렌더링 요청 권한을 원자적으로 선점한다. 아직 선점되지 않았거나 선점 시각이 staleBefore보다
     * 이전일 때만 성공(true)하므로, 여러 인스턴스가 같은 건을 동시에 요청하지 않는다.
     */
    /** 같은 상품에 기준 시각보다 나중에 요청됐고 FAILED가 아닌 생성 건이 있는지. */
    boolean existsNewerNonFailed(Long productId, LocalDateTime requestedAfter);

    boolean claimRender(Long id, LocalDateTime now, LocalDateTime staleBefore);
    Optional<ContentGeneration> findByIdAndProductId(Long id, Long productId);
    Optional<ContentGeneration> findByIdempotencyKey(String idempotencyKey);
    List<ContentGeneration> findAllByStatusAndRequestedAtBefore(GenerationStatus status, LocalDateTime deadline);
    List<ContentGeneration> findAllByStatus(GenerationStatus status);
    Optional<ContentGeneration> findFirstByProductIdAndStatusOrderByRequestedAtDesc(Long productId, GenerationStatus status);
}
