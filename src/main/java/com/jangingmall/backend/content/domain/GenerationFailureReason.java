package com.jangingmall.backend.content.domain;

/**
 * 생성 건이 FAILED가 된 이유. 내부 기록용이며 API 응답에는 노출하지 않는다.
 * 마감 초과로 실패한 건은 결과가 늦게 도착하면 COMPLETED로 되돌릴 수 있지만, AI가 실패를 확정한 건은 그렇지 않다.
 */
public enum GenerationFailureReason {
    /** AI에 제출하지 못한 채 제출 마감(31분)이 지났다. */
    SUBMIT_DEADLINE,
    /** AI가 접수했지만 초안 마감(31분)까지 끝나지 않았다. */
    AI_DEADLINE,
    /** AI가 작업 실패를 확정했다. */
    AI_FAILED,
    /** 초안 이후 렌더링 마감(기본 3시간)까지 결과가 오지 않았다. */
    RENDER_DEADLINE
}
