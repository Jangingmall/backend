-- AI 초안 완료(DRAFT_READY) 시각. 렌더링 마감 계산용 내부 컬럼이며 API 응답에는 노출하지 않는다.
ALTER TABLE content_generation ADD COLUMN draft_ready_at TIMESTAMP;

-- 렌더링 요청을 마지막으로 선점한 시각. 여러 인스턴스가 같은 건을 동시에 요청하지 않도록 원자적으로 선점하는 데 쓴다.
ALTER TABLE content_generation ADD COLUMN render_claimed_at TIMESTAMP;
