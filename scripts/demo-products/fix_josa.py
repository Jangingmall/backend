#!/usr/bin/env python3
"""'손으로 만든 {품목}이라 사진과 …' 에서 받침 없는 품목(노리개·스카프 등)은 '이라'가 아니라 '라'여야 한다.
이미 적용된 V22·V24·V26·V30 은 고칠 수 없어서(체크섬), 본문 블록과 상세 JSON 의 문장을 고치는 V31 을 만든다.
실행: python3 scripts/demo-products/fix_josa.py
"""
import glob
import os
import re

HERE = os.path.dirname(__file__)
MIG = os.path.join(HERE, "..", "..", "src", "main", "resources", "db", "migration")
OUT = os.path.join(MIG, "V31__fix_josa_ira.sql")


def has_batchim(word):
    code = ord(word[-1])
    return 0xAC00 <= code <= 0xD7A3 and (code - 0xAC00) % 28 != 0


def main():
    words = set()
    for path in glob.glob(os.path.join(MIG, "V*.sql")):
        if path == OUT:
            continue
        text = open(path, encoding="utf-8").read()
        words.update(re.findall(r"만든 ([가-힣 ]+?)이라 사진과", text))
    # 품목은 공백 없는 한 단어이거나 '자수 액자'처럼 마지막 낱말만 보면 된다
    items = sorted({w.split()[-1] for w in words})
    fix = [w for w in items if not has_batchim(w)]
    pairs = ",\n".join(f"    ('{w}이라 사진과', '{w}라 사진과')" for w in fix)
    sql = f"""-- '손으로 만든 {{품목}}이라 사진과 …' 문장에서 받침 없는 품목은 '라'로 고친다(예: 노리개이라 → 노리개라). 원본: scripts/demo-products/fix_josa.py
-- 대상 품목: {", ".join(fix)}

CREATE TEMP TABLE demo_josa (wrong TEXT, fixed TEXT) ON COMMIT DROP;
INSERT INTO demo_josa VALUES
{pairs};

UPDATE content_block b SET text = REPLACE(b.text, j.wrong, j.fixed)
FROM demo_josa j WHERE b.text LIKE '%' || j.wrong || '%';

UPDATE content c SET react_document = REPLACE(c.react_document, j.wrong, j.fixed), updated_at = NOW()
FROM demo_josa j WHERE c.react_document LIKE '%' || j.wrong || '%';
"""
    open(OUT, "w", encoding="utf-8").write(sql)
    print(len(fix), "items ->", OUT, fix)


if __name__ == "__main__":
    main()
