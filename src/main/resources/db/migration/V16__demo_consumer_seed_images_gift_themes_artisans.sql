-- 소비자 페이지 시연용 데이터 정리 (홈 · 선물 · 장인관 · 상품 상세 · 주문서)
--
-- 1) 대표 이미지: 외부 Unsplash 링크와 비어 있거나 밀려 들어간 값(지역명 등)을 소분류별 일러스트로 교체한다(STG 이미지 서버).
--    직접 업로드한 이미지(product_image)가 있는 상품은 건드리지 않는다.
-- 2) 상품명 끝의 "(더미)" 표기를 지운다.
-- 3) 선물 테마(product_gift_theme)가 비어 있어 선물 섹션이 빈 화면이므로, 소분류에 맞는 테마를 채운다.
-- 4) 장인관 캐러셀(인기순 6명)이 보여 줄 소개 문구·경력·등급을 채운다. 시연용 더미 값이다.

-- 1) 대표 이미지
--    소분류(1~56)마다 STG 이미지 서버(https://img.stg.midam.store)에 올린 일러스트 imageId 를 짝지었다.
--    일러스트는 직접 그린 도형 그림이라 사람·실제 사진·상표가 없다(scripts/seed-images/generate.py).
UPDATE product p
SET thumbnail_url = 'https://img.stg.midam.store/images/product/63/' || m.image_id || '/1280w.webp'
FROM (VALUES
    (1, '01M3VATRMFHW53E73R8HSRKA2E'),
    (2, '01M3VATXA5MRM87RJHF3PHGYVP'),
    (3, '01M3VAV21QS19D6KTDE5Q7F1KQ'),
    (4, '01M3VAV7WWR156TK7Q8K3M945Q'),
    (5, '01M3VAVC7WMTBRG7RS2E4RW7F2'),
    (6, '01M3VAVGB7Z79QG054GRJRC8ZB'),
    (7, '01M3VAVMHNEEWMHZ6R6YM6VWDK'),
    (8, '01M3VAVRXBGNV2HX8P0KKGQKK7'),
    (9, '01M3VAVX3RRGFHS6HTKCV4BH55'),
    (10, '01M3VAW1D70VH19W03AQA50NBP'),
    (11, '01M3VAW5HA5B9SV306JJ6FSJJN'),
    (12, '01M3VAWA037JRWW70F937QMXRB'),
    (13, '01M3VAWEC62QWTN9E5H6JQGK63'),
    (14, '01M3VAWJTWPPTK87542XM1W48A'),
    (15, '01M3VAWPZKFS2JWWMW9ZE62MBA'),
    (16, '01M3VAWV45Y05K3QZXXDMC0GRM'),
    (17, '01M3VAWZD6AA80S95381CPQ9F8'),
    (18, '01M3VAX4NYYY8KRXNZB89ME4Q3'),
    (19, '01M3VAX8ZXGWPA22SRGNNZWFGK'),
    (20, '01M3VAXD8A184THXHXSD8SVTEM'),
    (21, '01M3VAXHGE0FEH9P8696545DMK'),
    (22, '01M3VAXNVMMPE3BMR82M175F5H'),
    (23, '01M3VAXT5M0PC91YMM4V3GAEMW'),
    (24, '01M3VAXY8WZQ66KXEKJCFTV8M7'),
    (25, '01M3VAY2JC89RQ9G6JXBGV7HS4'),
    (26, '01M3VAY71M08K9CWDPS9YFMS8R'),
    (27, '01M3VAYBCTVREDWV71NTA1SAX0'),
    (28, '01M3VAYFGY34EYHMZYBP51XX2T'),
    (29, '01M3VAYKTPP1DKK6FXPR423DN7'),
    (30, '01M3VAYR6FKC3WRCRT9P3EYZZE'),
    (31, '01M3VAYWEQ4KDVZENFWV2TGRXD'),
    (32, '01M3VAZ0V8WN2BHNAS8XVQPD97'),
    (33, '01M3VAZ50616QHZGKAP56AKNP3'),
    (34, '01M3VAZ9C4BEGC8D7Y8FVHDKC2'),
    (35, '01M3VAZDJQ0TNPX66NCRPTVRR6'),
    (36, '01M3VAZHT3V8NV80157M8PFZ2E'),
    (37, '01M3VAZNZNC96N7XCXZ3S8M14N'),
    (38, '01M3VAZTB7A0Z4HEPPQY9CR0T5'),
    (39, '01M3VAZYG5989A0R2YWX4G4N1F'),
    (40, '01M3VB02STYB59CTMQ3H17GYZX'),
    (41, '01M3VB06WAXE6M5T5KQF7HVVBP'),
    (42, '01M3VB0B7HGAS8J9HE60FZN7G4'),
    (43, '01M3VB0FBTQ0KTRE99AWB7X3PR'),
    (44, '01M3VB0KGRG77HKT73B80RF3TE'),
    (45, '01M3VB0QNJDVFMZ814J7M8BBFT'),
    (46, '01M3VB0VV4DT4NS85DNKMHYHBX'),
    (47, '01M3VB10AY1E5P4F5KMSV2S7ZT'),
    (48, '01M3VB14P2WB82X7RQ13Y8G0C3'),
    (49, '01M3VB195B0F54C3VMCJYXDK9C'),
    (50, '01M3VB1DR8YCT3D5NVY0Y304A5'),
    (51, '01M3VB1HYSVVXX0Y8E0PQTWM1T'),
    (52, '01M3VB1P1VJHPTVCF2MJXNV58Q'),
    (53, '01M3VB1T7CQ0KP7N2JZ92VKPS1'),
    (54, '01M3VB1YGWF65YDTMVZ4T57VSH'),
    (55, '01M3VB22MMV7Z6PNXK703NCCF8'),
    (56, '01M3VB28Z1KGEY851FJ9PAXPZZ')
) AS m(subcategory_id, image_id)
WHERE p.subcategory_id = m.subcategory_id
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
