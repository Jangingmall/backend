#!/usr/bin/env python3
"""실사 이미지(ZIP·폴더)를 WebP 로 바꾸고, 해당 상품에 연결하는 SQL(V20)을 만든다.

흐름: 검증(이름 규칙·정사각형·용량) → WebP 변환(긴 변 1280px 이하, 10MB 이하) → docs/demo-products/photoreal/ 에 저장 →
      V20 마이그레이션(또는 배포 없이 직접 실행할 SQL) 생성. 상품은 상품명+장인 ID(V17 자연키)로 찾는다.

연결 규칙
  A → product.thumbnail_url(대표)      B → product_detail_image 0번(상세 1번)      C → product_detail_image 1번(상세 2번)
  B·C 가 없으면 기존 상세 이미지를 그대로 둔다. 큐레이션 상품(V17)의 이미지만 바꾼다.

사용
  python3 scripts/demo-products/photoreal_apply.py photoreal.zip                  # WebP 저장 + V20 생성
  python3 scripts/demo-products/photoreal_apply.py photoreal.zip --manual-sql     # 배포 없이 DB 에 직접 실행할 SQL 로 생성
  python3 scripts/demo-products/photoreal_apply.py photoreal.zip --dry-run        # 검증과 계획만 출력
"""
import argparse
import json
import os
import re
import sys

from PIL import Image

HERE = os.path.dirname(__file__)
ROOT = os.path.join(HERE, "..", "..")
IMG_DIR = os.path.join(ROOT, "docs", "demo-products", "photoreal")
TEMPLATE = os.path.join(IMG_DIR, "manifest.template.json")
MIGRATION = os.path.join(ROOT, "src", "main", "resources", "db", "migration", "V20__demo_photoreal_images.sql")
RAW = "https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal"
NAME = re.compile(r"^(p\d{2})_([ABC])\.(png|jpg|jpeg|webp)$", re.IGNORECASE)


def sql_text(value):
    return "'" + value.replace("'", "''") + "'"


def collect(source):
    import tempfile
    import zipfile
    files, root = {}, source
    if zipfile.is_zipfile(source):
        root = tempfile.mkdtemp(prefix="photoreal-")
        with zipfile.ZipFile(source) as archive:
            archive.extractall(root)
    for folder, _dirs, names in os.walk(root):
        if "__MACOSX" in folder:
            continue
        for name in names:
            if not name.startswith("."):
                files.setdefault(name, os.path.join(folder, name))
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("source")
    parser.add_argument("--manual-sql", action="store_true", help="마이그레이션 대신 직접 실행할 SQL(docs/…/apply-manually.sql)을 만든다")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--max-side", type=int, default=1280)
    parser.add_argument("--images-dir", default=IMG_DIR)
    parser.add_argument("--sql-out")
    args = parser.parse_args()

    items = {item["key"]: item for item in json.load(open(TEMPLATE, encoding="utf-8"))["items"]}
    files = collect(args.source)
    errors, picked = [], {}
    for name, path in sorted(files.items()):
        m = NAME.match(name)
        if not m:
            continue
        key, cut = m.group(1).lower(), m.group(2).upper()
        if key not in items:
            errors.append(f"대상 아닌 키: {name}")
            continue
        with Image.open(path) as image:
            width, height = image.size
        if abs(width - height) > max(width, height) * 0.02:
            errors.append(f"정사각형 아님: {name} {width}x{height}")
        picked.setdefault(key, {})[cut] = path
    for key in items:
        if "A" not in picked.get(key, {}):
            errors.append(f"대표(A) 없음: {key}")
    if errors:
        print("[오류] 아래를 고친 뒤 다시 실행하세요.")
        for line in errors:
            print("  -", line)
        sys.exit(1)

    rows = []
    for key, cuts in picked.items():
        urls = {}
        for cut, path in cuts.items():
            target = os.path.join(args.images_dir, f"{key}_{cut}.webp")
            if not args.dry_run:
                os.makedirs(args.images_dir, exist_ok=True)
                with Image.open(path) as image:
                    image = image.convert("RGB")
                    image.thumbnail((args.max_side, args.max_side), Image.LANCZOS)
                    quality = 90
                    while True:
                        image.save(target, "WEBP", quality=quality, method=6)
                        if os.path.getsize(target) <= 10 * 1024 * 1024 or quality <= 50:
                            break
                        quality -= 10
            urls[cut] = f"{RAW}/{key}_{cut}.webp"
        m = items[key]["match"]
        rows.append((m["title"], m["artisanId"], urls.get("A"), urls.get("B"), urls.get("C")))

    values = ",\n".join(
        f"    ({sql_text(t)}, {a}, {sql_text(ua)}, {sql_text(ub) if ub else 'NULL'}, {sql_text(uc) if uc else 'NULL'})"
        for t, a, ua, ub, uc in rows)
    header = ("-- 시연용 실사풍 이미지(AI 생성)로 큐레이션 상품의 대표·상세 이미지를 바꾼다.\n"
              "-- 원본: scripts/demo-products/photoreal_apply.py  (이미지는 docs/demo-products/photoreal/*.webp)\n"
              "-- 큐레이션 상품(V17: thumbnail_url 이 docs/demo-* 인 것)만 상품명+장인 ID 로 찾아 바꾼다. B·C 가 없으면 기존 상세 이미지를 둔다.\n")
    body = f"""
CREATE TEMP TABLE demo_photoreal (title VARCHAR(200), artisan_id BIGINT, main_url VARCHAR(500),
    detail0_url VARCHAR(500), detail1_url VARCHAR(500)) ON COMMIT DROP;
INSERT INTO demo_photoreal VALUES
{values};

UPDATE product_detail_image d SET image_url = r.detail0_url
FROM product p JOIN demo_photoreal r ON r.title = p.title AND r.artisan_id = p.artisan_id
WHERE d.product_id = p.product_id AND d.display_order = 0 AND r.detail0_url IS NOT NULL
  AND p.thumbnail_url LIKE '%/docs/demo-%';

UPDATE product_detail_image d SET image_url = r.detail1_url
FROM product p JOIN demo_photoreal r ON r.title = p.title AND r.artisan_id = p.artisan_id
WHERE d.product_id = p.product_id AND d.display_order = 1 AND r.detail1_url IS NOT NULL
  AND p.thumbnail_url LIKE '%/docs/demo-%';

UPDATE product p SET thumbnail_url = r.main_url
FROM demo_photoreal r
WHERE r.title = p.title AND r.artisan_id = p.artisan_id AND p.thumbnail_url LIKE '%/docs/demo-%';
"""
    if args.manual_sql:
        out = args.sql_out or os.path.join(args.images_dir, "apply-manually.sql")
        text = header.replace("-- 시연용", "-- [배포 없이 직접 실행] 시연용", 1) + "BEGIN;\n" + body.replace("ON COMMIT DROP", "ON COMMIT DROP") + "\nCOMMIT;\n"
    else:
        out = args.sql_out or MIGRATION
        text = header + body
    print(f"상품 {len(rows)}개 (B {sum(1 for r in rows if r[3])}, C {sum(1 for r in rows if r[4])}) -> {'계획만' if args.dry_run else out}")
    if not args.dry_run:
        os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
        open(out, "w", encoding="utf-8").write(text)


if __name__ == "__main__":
    main()
