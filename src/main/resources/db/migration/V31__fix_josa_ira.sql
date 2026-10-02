-- '손으로 만든 {품목}이라 사진과 …' 문장에서 받침 없는 품목은 '라'로 고친다(예: 노리개이라 → 노리개라). 원본: scripts/demo-products/fix_josa.py
-- 대상 품목: 노리개, 도구, 보자기, 부채, 스카프, 식탁보, 앞치마, 액자, 장신구, 지류, 침구, 필기구

CREATE TEMP TABLE demo_josa (wrong TEXT, fixed TEXT) ON COMMIT DROP;
INSERT INTO demo_josa VALUES
    ('노리개이라 사진과', '노리개라 사진과'),
    ('도구이라 사진과', '도구라 사진과'),
    ('보자기이라 사진과', '보자기라 사진과'),
    ('부채이라 사진과', '부채라 사진과'),
    ('스카프이라 사진과', '스카프라 사진과'),
    ('식탁보이라 사진과', '식탁보라 사진과'),
    ('앞치마이라 사진과', '앞치마라 사진과'),
    ('액자이라 사진과', '액자라 사진과'),
    ('장신구이라 사진과', '장신구라 사진과'),
    ('지류이라 사진과', '지류라 사진과'),
    ('침구이라 사진과', '침구라 사진과'),
    ('필기구이라 사진과', '필기구라 사진과');

UPDATE content_block b SET text = REPLACE(b.text, j.wrong, j.fixed)
FROM demo_josa j WHERE b.text LIKE '%' || j.wrong || '%';

UPDATE content c SET react_document = REPLACE(c.react_document, j.wrong, j.fixed), updated_at = NOW()
FROM demo_josa j WHERE c.react_document LIKE '%' || j.wrong || '%';
