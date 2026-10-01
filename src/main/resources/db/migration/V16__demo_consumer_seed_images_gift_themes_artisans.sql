-- 소비자 페이지 시연용 데이터 정리 (홈 · 선물 · 장인관 · 상품 상세 · 주문서)
--
-- 1) 대표 이미지: 외부 Unsplash 링크와 비어 있거나 밀려 들어간 값(지역명 등)을 소분류별 일러스트(외부 링크, WebP)로 교체한다.
--    직접 업로드한 이미지(product_image)가 있는 상품은 건드리지 않는다.
-- 2) 상품명 끝의 "(더미)" 표기를 지운다.
-- 3) 선물 테마(product_gift_theme)가 비어 있어 선물 섹션이 빈 화면이므로, 소분류에 맞는 테마를 채운다.
-- 4) 장인관 캐러셀(인기순 6명)이 보여 줄 소개 문구·경력·등급을 채운다. 시연용 더미 값이다.

-- 1) 대표 이미지
--    소분류(1~56)마다 직접 그린 일러스트(WebP)를 저장소 파일(docs/seed-images/sub-NN.webp)의 외부 링크로 연결한다.
--    서버(S3/CDN)가 꺼져 있어도 열리고, 업로드 만료(24시간)가 없다. 형식·크기는 이미지 규격과 같다(WebP, 10MB·10,000px 이하).
--    일러스트는 사람·실제 사진·상표가 없는 도형 그림이다(scripts/seed-images/generate.py).
--    S3 버전으로 바꾸려면 docs/demo-images/switch-image-source.sql 을 쓴다.
UPDATE product p
SET thumbnail_url = 'https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/seed-images/sub-'
        || lpad(p.subcategory_id::text, 2, '0') || '.webp'
WHERE p.subcategory_id BETWEEN 1 AND 56
  AND NOT EXISTS (SELECT 1 FROM product_image pi WHERE pi.product_id = p.product_id);

-- 2) 상품명
UPDATE product
SET title = btrim(replace(title, '(더미)', ''))
WHERE title LIKE '%(더미)%';

-- 3) 선물 테마
INSERT INTO product_gift_theme (product_id, gift_theme)
SELECT p.product_id, t.gift_theme
FROM product p
JOIN (VALUES
    ('HOUSEWARMING', 1), ('HOUSEWARMING', 2), ('HOUSEWARMING', 10), ('HOUSEWARMING', 11), ('HOUSEWARMING', 12),
    ('HOUSEWARMING', 15), ('HOUSEWARMING', 34), ('HOUSEWARMING', 36), ('HOUSEWARMING', 41), ('HOUSEWARMING', 42),
    ('BIRTHDAY_60TH', 1), ('BIRTHDAY_60TH', 26), ('BIRTHDAY_60TH', 34), ('BIRTHDAY_60TH', 35), ('BIRTHDAY_60TH', 51),
    ('BIRTHDAY_60TH', 52), ('BIRTHDAY_60TH', 55), ('BIRTHDAY_60TH', 56),
    ('WEDDING', 3), ('WEDDING', 5), ('WEDDING', 18), ('WEDDING', 35), ('WEDDING', 37), ('WEDDING', 41),
    ('WEDDING', 43), ('WEDDING', 56),
    ('BOSS', 1), ('BOSS', 29), ('BOSS', 31), ('BOSS', 32), ('BOSS', 33), ('BOSS', 44), ('BOSS', 52),
    ('PARENTS', 21), ('PARENTS', 26), ('PARENTS', 34), ('PARENTS', 35), ('PARENTS', 51), ('PARENTS', 52),
    ('PARENTS', 55), ('PARENTS', 56),
    ('FRIEND', 17), ('FRIEND', 19), ('FRIEND', 22), ('FRIEND', 23), ('FRIEND', 24), ('FRIEND', 25), ('FRIEND', 38),
    ('PROMOTION', 10), ('PROMOTION', 23), ('PROMOTION', 31), ('PROMOTION', 32), ('PROMOTION', 33), ('PROMOTION', 44),
    ('CORPORATE', 2), ('CORPORATE', 32), ('CORPORATE', 51), ('CORPORATE', 52), ('CORPORATE', 55), ('CORPORATE', 56)
) AS t(gift_theme, subcategory_id) ON t.subcategory_id = p.subcategory_id
WHERE NOT EXISTS (
    SELECT 1 FROM product_gift_theme g WHERE g.product_id = p.product_id AND g.gift_theme = t.gift_theme
);

-- 4) 장인관 (등록 상품이 가장 많은 공방 6곳을 인기순 상위로 올린다)
UPDATE artisan_profile a
SET popularity_score = v.score,
    certification_level = '우수 장인',
    career_years = v.career_years,
    quote = v.quote
FROM (VALUES
    (31, 99, 32, '한 땀 한 땀, 입는 분의 하루를 지어 드립니다'),
    (36, 98, 18, '작은 소품에 전통의 멋을 담았습니다'),
    (25, 97, 25, '손끝으로 놓은 수가 집 안의 온기가 됩니다'),
    (32, 96, 28, '바람이 지나가듯 가벼운 한산모시의 결'),
    (34, 95, 30, '은의 빛이 오래도록 곁에 머물도록'),
    (40, 94, 22, '쓰는 이의 품격을 더하는 나전칠기')
) AS v(artisan_id, score, career_years, quote)
WHERE a.artisan_id = v.artisan_id;
