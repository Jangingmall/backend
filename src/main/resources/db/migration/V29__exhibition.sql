-- 기획전: 제목·소개·배너와 상품 묶음(순서 포함)을 가진다.
CREATE TABLE exhibition (
    exhibition_id     BIGSERIAL     PRIMARY KEY,
    title             VARCHAR(100)  NOT NULL,
    subtitle          VARCHAR(200),
    description       TEXT,
    banner_image_url  VARCHAR(500),
    display_order     INT           NOT NULL DEFAULT 0,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP     NOT NULL
);

CREATE TABLE exhibition_product (
    exhibition_id  BIGINT NOT NULL REFERENCES exhibition(exhibition_id) ON DELETE CASCADE,
    product_id     BIGINT NOT NULL REFERENCES product(product_id) ON DELETE CASCADE,
    display_order  INT    NOT NULL,
    PRIMARY KEY (exhibition_id, product_id)
);
CREATE INDEX idx_exhibition_active_order ON exhibition (active, display_order);

-- 시연 기획전 2개
INSERT INTO exhibition (title, subtitle, description, banner_image_url, display_order, active, created_at) VALUES
    ('장인이 빚은 공간의 온기', '집 안에 들이고 싶은 공예 4선',
     '대나무를 엮은 조명부터 맑은 백토 잔, 자연에서 얻은 색으로 물들인 러너까지. 공간에 은은한 분위기를 더하는 장인의 작품을 모았습니다.',
     (SELECT thumbnail_url FROM product WHERE title = '대나무 조명' LIMIT 1), 1, TRUE, NOW()),
    ('바람을 부르는 부채 모음', '한 해 여름을 함께할 부채 5선',
     '합죽선과 산수화 부채, 옻칠 흑선과 모시 접선, 왕골 원형 부채까지. 손에 쥐는 순간 시원해지는 부채를 한자리에 모았습니다.',
     (SELECT thumbnail_url FROM product WHERE title = '산수화 대형 부채' LIMIT 1), 2, TRUE, NOW());

INSERT INTO exhibition_product (exhibition_id, product_id, display_order)
SELECT e.exhibition_id, p.product_id, v.ord
FROM (VALUES
    ('장인이 빚은 공간의 온기', '대나무 조명', 0), ('장인이 빚은 공간의 온기', '백잔', 1),
    ('장인이 빚은 공간의 온기', '자연염 테이블 러너', 2), ('장인이 빚은 공간의 온기', '산수화 대형 부채', 3),
    ('바람을 부르는 부채 모음', '전주 합죽선 · 매화선', 0), ('바람을 부르는 부채 모음', '산수화 대형 부채', 1),
    ('바람을 부르는 부채 모음', '옻칠 흑선', 2), ('바람을 부르는 부채 모음', '모시 여름 접선', 3),
    ('바람을 부르는 부채 모음', '왕골 원형 부채', 4)
) AS v(exhibition_title, product_title, ord)
JOIN exhibition e ON e.title = v.exhibition_title
JOIN product p ON p.title = v.product_title;
