-- 홈 기획전: giftTheme=EXHIBITION 으로 조회되는 기획전 상품(시연 디자인의 기획전 4개)을 표시한다.
INSERT INTO product_gift_theme (product_id, gift_theme)
SELECT p.product_id, 'EXHIBITION'
FROM product p
WHERE p.title IN ('대나무 조명', '백잔', '자연염 테이블 러너', '산수화 대형 부채')
  AND NOT EXISTS (SELECT 1 FROM product_gift_theme t WHERE t.product_id = p.product_id AND t.gift_theme = 'EXHIBITION');
