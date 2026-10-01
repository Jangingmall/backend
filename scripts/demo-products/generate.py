#!/usr/bin/env python3
"""시연용 큐레이션 상품 생성기 (catalog.py 가 원본).

만드는 것
  1) docs/demo-products/<key>.webp   상품별 일러스트(800×800 WebP, 사람·실제 사진·상표 없음)
  2) docs/demo-products/README.md    상품 표(이름·소개·특징·가격·선물 테마·이미지 주소)
  3) src/main/resources/db/migration/V17__demo_curated_products.sql   시드 마이그레이션

필요: pip install pillow, 한글 글꼴(wqy-zenhei 또는 nanum).
실행: python3 scripts/demo-products/generate.py
"""
import importlib.util
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
IMG_DIR = os.path.join(ROOT, "docs", "demo-products")
MIGRATION = os.path.join(ROOT, "src", "main", "resources", "db", "migration", "V17__demo_curated_products.sql")
RAW = "https://raw.githubusercontent.com/Jangingmall/backend/develop/docs"


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(ROOT, path))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


catalog = load("scripts/demo-products/catalog.py", "catalog")
seed = load("scripts/seed-images/generate.py", "seed_generate")


def make_image(font_path, product):
    top, bottom, accent, dark, light = catalog.PALETTES[product["color"]]
    size = seed.SIZE
    im = seed.gradient(top, bottom).convert("RGBA")
    overlay = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    d.ellipse((120, 140, 680, 620), fill=(255, 255, 255, 110))
    seed.draw(product["kind"], seed.Pen(d, accent, dark, light))
    im.alpha_composite(overlay)
    d2 = ImageDraw.Draw(im)
    d2.rectangle((0, 650, size, size), fill=seed.hexrgb(dark) + (255,))
    name = product["name"]
    font_size = 56
    font = ImageFont.truetype(font_path, font_size)
    while d2.textlength(name, font=font) > size - 60 and font_size > 28:
        font_size -= 2
        font = ImageFont.truetype(font_path, font_size)
    w = d2.textlength(name, font=font)
    d2.text(((size - w) / 2, 668), name, font=font, fill=seed.CREAM)
    small = ImageFont.truetype(font_path, 22)
    note = "MIDAM · 시연용 일러스트"
    d2.text(((size - d2.textlength(note, font=small)) / 2, 745), note, font=small, fill=seed.hexrgb(light))
    return im.convert("RGB")


def sql_text(value):
    return "'" + value.replace("'", "''") + "'"


def image_url(product):
    return f"{RAW}/{product['image']}" if product.get("image") else f"{RAW}/demo-products/{product['key']}.webp"


def write_images():
    font_path = next((f for f in seed.FONT_CANDIDATES if os.path.exists(f)), None)
    if not font_path:
        raise SystemExit("한글 글꼴을 찾을 수 없습니다.")
    os.makedirs(IMG_DIR, exist_ok=True)
    for product in catalog.PRODUCTS:
        if product.get("image"):
            continue
        make_image(font_path, product).save(os.path.join(IMG_DIR, f"{product['key']}.webp"), "WEBP", quality=88, method=6)


def write_readme():
    lines = ["# 시연용 큐레이션 상품 (33개)", "",
             "이름·소개·특징·이미지가 서로 맞도록 `scripts/demo-products/catalog.py` 한곳에서 관리한다. 이 문서와 이미지, 시드 마이그레이션(V17)은",
             "`python3 scripts/demo-products/generate.py` 로 다시 만든다. 이미지는 직접 그린 일러스트(WebP)이며 사람·실제 사진·상표가 없다.", "",
             "| 키 | 상품명 | 장인 | 소분류 | 가격 | 선물 테마 | 특징 | 이미지 |", "|---|---|---|---|---|---|---|---|"]
    for p in catalog.PRODUCTS:
        lines.append(f"| {p['key']} | {p['name']} | {p['artisan']} | {p['sub']} | {p['price']:,}원 | {', '.join(p['themes'])} | "
                     f"{' / '.join(p['features'])} | [보기]({image_url(p)}) |")
    lines += ["", "## 상품 소개", ""]
    for p in catalog.PRODUCTS:
        lines += [f"- **{p['name']}** — {p['intro']}"]
    with open(os.path.join(IMG_DIR, "README.md"), "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")


def write_migration():
    rows, themes, tags, colors = [], [], [], []
    for p in catalog.PRODUCTS:
        description = p["intro"] + " 특징: " + " · ".join(p["features"]) + "."
        rows.append("    (" + ", ".join([
            sql_text(p["name"]), str(p["artisan"]), str(p["sub"]), sql_text(description), sql_text(p["material"]),
            str(p["price"]), str(p["stock"]), sql_text(image_url(p)), str(p["days"])]) + ")")
        themes += [f"    ({sql_text(p['name'])}, {sql_text(t)})" for t in p["themes"]]
        tags += [f"    ({sql_text(p['name'])}, {sql_text(t)})" for t in p["tags"]]
        colors += [f"    ({sql_text(p['name'])}, {sql_text(c)})" for c in p["colors"]]
    sql = f"""-- 시연용 큐레이션 상품 {len(catalog.PRODUCTS)}개 (홈 · 선물 · 상세 · 주문서 시연)
-- 원본: scripts/demo-products/catalog.py  (python3 scripts/demo-products/generate.py 로 다시 만든다)
--
-- 상품명·소개·특징·이미지가 서로 맞도록 상품마다 직접 그린 일러스트(WebP)를 저장소 외부 링크로 연결한다.
-- 서버(S3/CDN)가 꺼져 있어도 열리고 업로드 만료가 없다. 이미 같은 이름의 상품이 있으면 다시 넣지 않는다.
-- 장인(artisan_id)·소분류(subcategory_id)는 시드 데이터 기준이다. 가격·재고·소개는 시연용 가짜 값이다.

WITH new_products AS (
    INSERT INTO product (artisan_id, category_id, subcategory_id, title, description, material, price, stock,
                         thumbnail_url, production_period_days, is_limited, is_custom_order, is_single_item,
                         has_gift_wrap, status, created_at, updated_at)
    SELECT v.artisan_id, s.category_id, v.subcategory_id, v.title, v.description, v.material, v.price, v.stock,
           v.thumbnail_url, v.days, FALSE, FALSE, FALSE, TRUE, 'ON_SALE', NOW(), NOW()
    FROM (VALUES
{(","+chr(10)).join(rows)}
    ) AS v(title, artisan_id, subcategory_id, description, material, price, stock, thumbnail_url, days)
    JOIN subcategory s ON s.subcategory_id = v.subcategory_id
    JOIN member m ON m.member_id = v.artisan_id
    WHERE NOT EXISTS (SELECT 1 FROM product e WHERE e.title = v.title AND e.artisan_id = v.artisan_id)
    RETURNING product_id, title
),
gift AS (
    INSERT INTO product_gift_theme (product_id, gift_theme)
    SELECT n.product_id, t.gift_theme FROM new_products n
    JOIN (VALUES
{(","+chr(10)).join(themes)}
    ) AS t(title, gift_theme) ON t.title = n.title
    RETURNING 1
),
purpose AS (
    INSERT INTO product_purpose_tag (product_id, purpose_tag)
    SELECT n.product_id, t.purpose_tag FROM new_products n
    JOIN (VALUES
{(","+chr(10)).join(tags)}
    ) AS t(title, purpose_tag) ON t.title = n.title
    RETURNING 1
)
INSERT INTO product_color (product_id, color)
SELECT n.product_id, t.color FROM new_products n
JOIN (VALUES
{(","+chr(10)).join(colors)}
) AS t(title, color) ON t.title = n.title;
"""
    with open(MIGRATION, "w", encoding="utf-8") as handle:
        handle.write(sql)


if __name__ == "__main__":
    write_images()
    write_readme()
    write_migration()
    print(f"상품 {len(catalog.PRODUCTS)}개: 이미지 {len(os.listdir(IMG_DIR)) - 1}개, README, V17 생성")
