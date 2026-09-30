-- 상품 Q&A 테이블 (ProductQuestion / ProductAnswer 엔티티). 기존 마이그레이션에 누락되어 prod/stg에서
-- "relation product_question does not exist" 오류 발생. 수동 생성된 환경을 고려해 IF NOT EXISTS 사용.

CREATE TABLE IF NOT EXISTS product_question (
    question_id BIGSERIAL PRIMARY KEY,
    product_id  BIGINT    NOT NULL,
    writer_id   BIGINT    NOT NULL,
    content     TEXT      NOT NULL,
    is_secret   BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL,
    CONSTRAINT fk_product_question_product FOREIGN KEY (product_id) REFERENCES product (product_id),
    CONSTRAINT fk_product_question_writer  FOREIGN KEY (writer_id)  REFERENCES member (member_id)
);

CREATE INDEX IF NOT EXISTS idx_product_question_product_created
    ON product_question (product_id, created_at DESC);

CREATE TABLE IF NOT EXISTS product_answer (
    answer_id   BIGSERIAL PRIMARY KEY,
    question_id BIGINT    NOT NULL UNIQUE,
    artisan_id  BIGINT    NOT NULL,
    content     TEXT      NOT NULL,
    answered_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_product_answer_question FOREIGN KEY (question_id) REFERENCES product_question (question_id),
    CONSTRAINT fk_product_answer_artisan  FOREIGN KEY (artisan_id)  REFERENCES member (member_id)
);
