-- 인기순: 점수 = 운영 노출 가중치 + 판매수×3 + 찜수×2 + 리뷰수. 운영 노출 가중치 컬럼을 추가한다(기본 0).
ALTER TABLE product ADD COLUMN popularity_boost INT NOT NULL DEFAULT 0;

-- 시연: 기본 목록(인기순) 맨 앞 8개를 고정 순서로 둔다. 가중치가 실제 집계 점수보다 훨씬 커서 순서가 바뀌지 않는다.
UPDATE product p SET popularity_boost = v.boost
FROM (VALUES
    ('전주 합죽선 · 매화선', 1000), ('청자 운학문 찻잔', 990), ('분청 귀얄 찻잔', 980), ('홍매 삼작 노리개', 970),
    ('수자수 모란도 액자', 960), ('한지 무드 조명', 950), ('하늘빛 한산모시 스카프', 940), ('나전 명함집', 930)
) AS v(title, boost)
WHERE p.title = v.title;

-- 시연: 홈 "베스트"(판매순 상위 5)가 되도록 판매 완료 주문을 심는다. 구매자는 시드 회원 1번이고 수량이 순위를 만든다.
CREATE TEMP TABLE demo_best (rank_no INT, title VARCHAR(200), quantity INT) ON COMMIT DROP;
INSERT INTO demo_best VALUES (1, '청자 분청 찻잔', 10), (2, '옻칠 원형 쟁반', 9), (3, '옥 매듭 반지', 8), (4, '전통 한지 무드등', 7), (5, '왕골 원형 부채', 6);

INSERT INTO orders (order_number, member_id, recipient_name, recipient_phone, zip_code, address1, status, total_amount,
                    payment_method, shipping_amount, created_at, updated_at)
SELECT 'DEMO-BEST-' || b.rank_no, 1, '시연 주문', '010-0000-0000', '00000', '시연용 주문', 'PURCHASE_CONFIRMED',
       p.price::BIGINT * b.quantity, 'CARD', 0, NOW(), NOW()
FROM demo_best b JOIN product p ON p.title = b.title
WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.order_number = 'DEMO-BEST-' || b.rank_no);

INSERT INTO order_item (order_id, product_id, product_name_snapshot, price_snapshot, quantity, total_price)
SELECT o.order_id, p.product_id, p.title, p.price, b.quantity, p.price::BIGINT * b.quantity
FROM demo_best b JOIN product p ON p.title = b.title
JOIN orders o ON o.order_number = 'DEMO-BEST-' || b.rank_no
WHERE NOT EXISTS (SELECT 1 FROM order_item i WHERE i.order_id = o.order_id);

-- 시연: 홈 "기획전"(찜 많은 순 상위 4)이 되도록 찜을 심는다. 찜한 회원 수가 순위를 만든다(상품마다 서로 다른 회원 N명).
CREATE TEMP TABLE demo_plan (title VARCHAR(200), likes INT) ON COMMIT DROP;
INSERT INTO demo_plan VALUES ('대나무 조명', 9), ('백잔', 8), ('자연염 테이블 러너', 7), ('산수화 대형 부채', 6);

INSERT INTO wishlist (member_id, product_id, created_at)
SELECT m.member_id, p.product_id, NOW()
FROM demo_plan d JOIN product p ON p.title = d.title
JOIN LATERAL (SELECT member_id FROM member ORDER BY member_id LIMIT d.likes) m ON TRUE
ON CONFLICT (member_id, product_id) DO NOTHING;
