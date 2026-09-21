-- PostgreSQL sequence reset after explicit-ID seed inserts
-- 이 파일은 local-postgresql 프로필에서만 실행된다.
SELECT setval(pg_get_serial_sequence('category',    'category_id'),    (SELECT MAX(category_id)    FROM category));
SELECT setval(pg_get_serial_sequence('subcategory', 'subcategory_id'), (SELECT MAX(subcategory_id) FROM subcategory));
SELECT setval(pg_get_serial_sequence('member',      'member_id'),      (SELECT MAX(member_id)      FROM member));
SELECT setval(pg_get_serial_sequence('product',     'product_id'),     (SELECT MAX(product_id)     FROM product));
