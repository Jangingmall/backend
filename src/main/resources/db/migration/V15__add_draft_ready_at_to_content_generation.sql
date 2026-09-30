-- AI 초안 완료(DRAFT_READY) 시각. 렌더링 30분 마감 계산용 내부 컬럼이며 API 응답에는 노출하지 않는다.
ALTER TABLE content_generation ADD COLUMN draft_ready_at TIMESTAMP;
