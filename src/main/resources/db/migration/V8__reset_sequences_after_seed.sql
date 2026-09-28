-- seed 데이터 삽입 후 시퀀스를 실제 최댓값으로 리셋
SELECT setval('member_member_id_seq',         (SELECT MAX(member_id)      FROM member));
SELECT setval('product_product_id_seq',       (SELECT MAX(product_id)     FROM product));
SELECT setval('category_category_id_seq',     (SELECT MAX(category_id)    FROM category));
SELECT setval('subcategory_subcategory_id_seq', (SELECT MAX(subcategory_id) FROM subcategory));
