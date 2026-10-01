#!/usr/bin/env python3
"""docs/demo-products/photoreal/staging/ 의 검수 통과 이미지를 상품에 연결하는 V20 마이그레이션을 만든다.

연결 규칙
  A → product.thumbnail_url(대표)   B → product_detail_image 0번   C → 1번
  - 큐레이션 33개(p01~p33): 상품명 + 장인 ID 로 찾는다(V17 자연키).
  - 대표 56개(f01~f56)와 상품별 이미지(dNNN_A): 상품명 + 소분류 ID 로 찾는다(V19 와 같은 방식).
  - A·B·C 가 모두 있으면 2번 이후 상세 이미지를 지워 실사만 보이게 한다.
  - A 만 있는 시드 상품은 상세 이미지를 지운다(일러스트와 실사가 섞이지 않게). 소재 확대(공용) 실사가 들어오면 그때 다시 채운다.
  - 시드로 들어간 상품(thumbnail_url 이 저장소 docs 이미지)만 바꾼다. 사용자가 만든 상품은 건드리지 않는다.
실행: python3 scripts/demo-products/photoreal_migration.py
"""
import json
import os
import re

HERE = os.path.dirname(__file__)
ROOT = os.path.join(HERE, "..", "..")
STAGING = os.path.join(ROOT, "docs", "demo-products", "photoreal", "staging")
README = os.path.join(ROOT, "docs", "demo-products", "README.md")
SEED = os.path.join(HERE, "seed_products.json")
MIGRATION = os.path.join(ROOT, "src", "main", "resources", "db", "migration", "V20__demo_photoreal_images.sql")
RAW = "https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging"
GUARD = "p.thumbnail_url LIKE '%/Jangingmall/backend/%/docs/%'"


def q(text):
    return "'" + text.replace("'", "''") + "'"


def staged():
    out = {}
    for name in os.listdir(STAGING):
        m = re.match(r"^((?:p\d\d)|(?:f\d\d)|(?:d\d{3}))_([ABC])\.webp$", name)
        if m:
            out.setdefault(m.group(1), {})[m.group(2)] = f"{RAW}/{name}"
    return out


def curated():
    rows = {}
    for line in open(README, encoding="utf-8"):
        m = re.match(r"\| (p\d\d) \| ([^|]+?) \| (\d+) \|", line)
        if m:
            rows[m.group(1)] = (m.group(2).strip(), int(m.group(3)))
    return rows


def main():
    images = staged()
    cur = curated()
    seed = json.load(open(SEED, encoding="utf-8"))
    by_id = {p["id"]: p for p in seed}
    flag = {}
    for p in sorted(seed, key=lambda x: x["id"]):
        flag.setdefault(p["sub"], p)
    c_rows, s_rows = [], []
    for key, urls in sorted(images.items()):
        if key.startswith("p"):
            title, artisan = cur[key]
            c_rows.append((title, artisan, urls))
        else:
            p = flag[int(key[1:])] if key.startswith("f") else by_id[int(key[1:])]
            s_rows.append((p["title"], p["sub"], urls))

    def values(rows):
        return ",\n".join("    (%s, %d, %s, %s, %s)" % (q(t), k, *(q(u.get(c)) if u.get(c) else "NULL" for c in "ABC"))
                          for t, k, u in rows)

    def block(table, keycol):
        join = f"r.title = p.title AND r.{keycol} = p.{'artisan_id' if keycol == 'artisan_id' else 'subcategory_id'}"
        return f"""
UPDATE product p SET thumbnail_url = r.main_url
FROM {table} r WHERE {join} AND r.main_url IS NOT NULL AND {GUARD};

INSERT INTO product_detail_image (product_id, display_order, image_url)
SELECT p.product_id, 0, r.detail0_url FROM product p JOIN {table} r ON {join}
WHERE r.detail0_url IS NOT NULL AND {GUARD}
ON CONFLICT (product_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url;

INSERT INTO product_detail_image (product_id, display_order, image_url)
SELECT p.product_id, 1, r.detail1_url FROM product p JOIN {table} r ON {join}
WHERE r.detail1_url IS NOT NULL AND {GUARD}
ON CONFLICT (product_id, display_order) DO UPDATE SET image_url = EXCLUDED.image_url;

DELETE FROM product_detail_image d USING product p JOIN {table} r ON {join}
WHERE d.product_id = p.product_id AND {GUARD}
  AND ((r.main_url IS NOT NULL AND r.detail0_url IS NOT NULL AND r.detail1_url IS NOT NULL AND d.display_order >= 2)
    OR (r.main_url IS NOT NULL AND r.detail0_url IS NULL AND r.detail1_url IS NULL));
"""

    text = f"""-- 시연용 실사풍 이미지(AI 생성)로 상품 대표·상세 이미지를 바꾼다. 원본: scripts/demo-products/photoreal_migration.py
-- 이미지는 docs/demo-products/photoreal/staging/*.webp. A=대표, B=상세 1번, C=상세 2번.
-- 큐레이션 {len(c_rows)}개는 상품명+장인 ID, 대표 56·상품별 이미지 {len(s_rows)}개는 상품명+소분류 ID 로 찾는다.
-- A·B·C 가 모두 있으면 2번 이후 상세를 지우고, A 만 있으면 상세를 지운다(일러스트가 섞이지 않게). 시드 상품(저장소 docs 이미지)만 바꾼다.

CREATE TEMP TABLE demo_photoreal_c (title VARCHAR(200), artisan_id BIGINT, main_url VARCHAR(500),
    detail0_url VARCHAR(500), detail1_url VARCHAR(500)) ON COMMIT DROP;
INSERT INTO demo_photoreal_c VALUES
{values(c_rows)};
{block("demo_photoreal_c", "artisan_id")}
CREATE TEMP TABLE demo_photoreal_s (title VARCHAR(200), subcategory_id BIGINT, main_url VARCHAR(500),
    detail0_url VARCHAR(500), detail1_url VARCHAR(500)) ON COMMIT DROP;
INSERT INTO demo_photoreal_s VALUES
{values(s_rows)};
{block("demo_photoreal_s", "subcategory_id")}"""
    open(MIGRATION, "w", encoding="utf-8").write(text)
    print(f"큐레이션 {len(c_rows)}개, 시드 {len(s_rows)}개 -> {MIGRATION}")


if __name__ == "__main__":
    main()
