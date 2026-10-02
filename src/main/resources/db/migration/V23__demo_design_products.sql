-- 시연 디자인(홈 베스트 5·기획전 4·신상품 4)의 상품 13개를 심는다. 원본: scripts/demo-products/seed_design_products.py
-- POPULAR(id 내림차순)에서 베스트 1~5번·기획전 6~9번, NEWEST 상위 4개가 신상품이 되도록 id 와 등록 시각을 정했다.
-- 디자인 원본 이미지가 있는 5개는 docs/demo-products/design/, 나머지는 비슷한 우리 실사 이미지를 쓴다.

INSERT INTO product (artisan_id, category_id, subcategory_id, title, description, material, price, stock,
                     thumbnail_url, production_period_days, is_limited, is_custom_order, is_single_item,
                     has_gift_wrap, status, created_at, updated_at)
SELECT v.artisan_id, s.category_id, v.subcategory_id, v.title, v.description, v.material, v.price, v.stock,
       v.thumbnail_url, v.days, FALSE, FALSE, FALSE, FALSE, 'ON_SALE', v.created_at, NOW()
FROM (VALUES
    (1, '옻칠 흑선', 41, 25, '한지에 옻칠을 입혀 검게 마감한 접부채입니다. 펼치면 깊은 윤기가 돌고 접으면 단정한 선이 남습니다.', '한지 대나무 옻칠', 120000, 30, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/f25_C.webp', 14, NOW() - (4 * INTERVAL '1 second')),
    (2, '모시 여름 접선', 32, 25, '대나무 뼈대에 모시를 입혀 접어 쓰는 여름 부채입니다. 결이 고와 바람이 가볍게 일고 보관도 간편합니다.', '한산모시 대나무', 28000, 30, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/f25_A.webp', 14, NOW() - (3 * INTERVAL '1 second')),
    (3, '전통 매화 은반지', 34, 17, '은을 두드려 매화 가지와 꽃을 한 땀씩 새긴 반지입니다. 쓸수록 은 특유의 부드러운 빛이 깊어집니다.', '순은', 56000, 30, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/design/new-plum-silver-ring.webp', 14, NOW() - (2 * INTERVAL '1 second')),
    (4, '청사초롱 벽등', 25, 10, '홍색과 청색 한지를 두른 청사초롱 모양의 벽등입니다. 불을 켜면 격자 문양 사이로 붉고 푸른 빛이 번집니다.', '한지 원목', 95000, 30, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/design/new-cheongsa-lantern.webp', 14, NOW() - (1 * INTERVAL '1 second')),
    (5, '산수화 대형 부채', 41, 25, '한지 위에 먹과 담채로 산수를 그린 대형 접부채입니다. 펼쳐 두면 그대로 한 폭의 그림이 됩니다.', '한지 대나무', 85000, 30, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_plan4_A.webp', 14, NOW() - INTERVAL '1 day'),
    (6, '자연염 테이블 러너', 32, 36, '자연에서 얻은 색으로 물들인 모시 러너입니다. 상차림 가운데 길게 깔면 은은한 결이 식탁을 단정하게 만듭니다.', '한산모시', 140000, 30, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_plan3_A.webp', 14, NOW() - INTERVAL '1 day'),
    (7, '백잔', 13, 1, '맑은 백토로 빚어 투명한 유약을 입힌 작은 잔입니다. 차의 빛깔이 그대로 비쳐 보입니다.', '백자', 100000, 30, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_plan2_A.webp', 14, NOW() - INTERVAL '1 day'),
    (8, '대나무 조명', 22, 10, '가는 대나무를 하나씩 엮어 만든 조명입니다. 불을 켜면 엮은 결을 따라 따뜻한 그림자가 번집니다.', '대나무', 500000, 30, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_plan1_A.webp', 14, NOW() - INTERVAL '1 day'),
    (9, '왕골 원형 부채', 37, 25, '왕골을 한 올씩 엮어 둥글게 짠 부채입니다. 바람이 부드럽게 일고 여름 내내 곁에 두기 좋습니다.', '왕골', 22000, 30, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_best5_A.webp', 14, NOW() - INTERVAL '1 day'),
    (10, '전통 한지 무드등', 25, 10, '손으로 뜬 한지를 원통형 살대에 바른 스탠드입니다. 불을 켜면 한지 결 사이로 따뜻한 빛이 퍼집니다.', '한지 원목', 68000, 30, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_best4_A.webp', 14, NOW() - INTERVAL '1 day'),
    (11, '옥 매듭 반지', 34, 17, '결 고운 옥을 갈고 닦아 매듭 모양을 새긴 반지입니다. 착용할수록 옥의 윤기가 깊어집니다.', '천연 옥', 400000, 30, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_best3_A.webp', 14, NOW() - INTERVAL '1 day'),
    (12, '옻칠 원형 쟁반', 18, 5, '나무를 둥글게 깎아 옻칠을 여러 번 덧발라 말린 쟁반입니다. 찻잔과 다과를 올리면 붉은 윤기가 상을 단정하게 받쳐 줍니다.', '원목 옻칠', 95000, 30, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_best2_A.webp', 14, NOW() - INTERVAL '1 day'),
    (13, '청자 분청 찻잔', 20, 1, '연한 비색 유약을 입혀 구운 찻잔입니다. 손에 쥐면 가볍고, 따뜻한 차를 따르면 유약 속 빛이 은은하게 올라옵니다.', '청자', 120000, 30, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_best1_A.webp', 14, NOW() - INTERVAL '1 day')
) AS v(ord, title, artisan_id, subcategory_id, description, material, price, stock, thumbnail_url, days, created_at)
JOIN subcategory s ON s.subcategory_id = v.subcategory_id
WHERE NOT EXISTS (SELECT 1 FROM product e WHERE e.title = v.title AND e.artisan_id = v.artisan_id)
ORDER BY v.ord;

INSERT INTO product_color (product_id, color)
SELECT p.product_id, c.color
FROM (VALUES
    ('청자 분청 찻잔', 20, '연청자'),
    ('옻칠 원형 쟁반', 18, '갈색'),
    ('옻칠 원형 쟁반', 18, '흑색'),
    ('옥 매듭 반지', 34, '연옥색'),
    ('전통 한지 무드등', 25, '호박색'),
    ('왕골 원형 부채', 37, '살구색'),
    ('왕골 원형 부채', 37, '황토색'),
    ('청사초롱 벽등', 25, '홍색'),
    ('청사초롱 벽등', 25, '청색'),
    ('전통 매화 은반지', 34, '은색'),
    ('모시 여름 접선', 32, '백색'),
    ('모시 여름 접선', 32, '미색'),
    ('모시 여름 접선', 32, '연하늘색'),
    ('옻칠 흑선', 41, '흑색'),
    ('대나무 조명', 22, '황토색'),
    ('백잔', 13, '백색'),
    ('자연염 테이블 러너', 32, '회갈색'),
    ('자연염 테이블 러너', 32, '분홍'),
    ('자연염 테이블 러너', 32, '녹색'),
    ('산수화 대형 부채', 41, '미색'),
    ('산수화 대형 부채', 41, '백색')
) AS c(title, artisan_id, color)
JOIN product p ON p.title = c.title AND p.artisan_id = c.artisan_id
ON CONFLICT DO NOTHING;

INSERT INTO product_detail_image (product_id, display_order, image_url)
SELECT p.product_id, d.display_order, d.image_url
FROM (VALUES
    ('청자 분청 찻잔', 20, 0, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_best1_B.webp'),
    ('청자 분청 찻잔', 20, 1, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_best1_C.webp'),
    ('옻칠 원형 쟁반', 18, 0, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_best2_B.webp'),
    ('옻칠 원형 쟁반', 18, 1, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_best2_C.webp'),
    ('옥 매듭 반지', 34, 0, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_best3_B.webp'),
    ('옥 매듭 반지', 34, 1, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_best3_C.webp'),
    ('전통 한지 무드등', 25, 0, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_best4_B.webp'),
    ('전통 한지 무드등', 25, 1, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_best4_C.webp'),
    ('왕골 원형 부채', 37, 0, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_best5_B.webp'),
    ('왕골 원형 부채', 37, 1, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_best5_C.webp'),
    ('대나무 조명', 22, 0, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_plan1_B.webp'),
    ('대나무 조명', 22, 1, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_plan1_C.webp'),
    ('백잔', 13, 0, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_plan2_B.webp'),
    ('백잔', 13, 1, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_plan2_C.webp'),
    ('자연염 테이블 러너', 32, 0, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_plan3_B.webp'),
    ('자연염 테이블 러너', 32, 1, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_plan3_C.webp'),
    ('산수화 대형 부채', 41, 0, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_plan4_B.webp'),
    ('산수화 대형 부채', 41, 1, 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging/home_plan4_C.webp')
) AS d(title, artisan_id, display_order, image_url)
JOIN product p ON p.title = d.title AND p.artisan_id = d.artisan_id
ON CONFLICT DO NOTHING;
