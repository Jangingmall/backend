-- 상품 목록 기본 정렬 최적화 (status 필터 + created_at 내림차순)
CREATE INDEX IF NOT EXISTS idx_product_status_created
    ON product (status, created_at DESC);

-- 카테고리별 상품 목록 (category_id + status)
CREATE INDEX IF NOT EXISTS idx_product_category_status
    ON product (category_id, status);

-- 서브카테고리별 상품 목록
CREATE INDEX IF NOT EXISTS idx_product_subcategory_status
    ON product (subcategory_id, status)
    WHERE subcategory_id IS NOT NULL;

-- pg_trgm 전문 검색 (title ILIKE '%keyword%')
-- local-postgresql 프로파일에서는 Flyway disabled이므로 prod/ci 전용
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_product_title_trgm
    ON product USING GIN (title gin_trgm_ops);

-- 주문 만료 스캔 보조 (CREATED 상태 주문만 인덱스 대상)
CREATE INDEX IF NOT EXISTS idx_orders_status_created_at
    ON orders (status, created_at)
    WHERE status = 'CREATED';
