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


GIFT_LABELS = {"HOUSEWARMING": "집들이·이사", "BIRTHDAY_60TH": "돌·환갑", "WEDDING": "웨딩·혼수", "BOSS": "상사·거래처",
               "PARENTS": "부모님·어른", "FRIEND": "친구·동료", "PROMOTION": "승진·감사", "CORPORATE": "기업·단체 답례품"}


def fit_font(draw, font_path, text, max_width, start, minimum=22):
    size = start
    font = ImageFont.truetype(font_path, size)
    while draw.textlength(text, font=font) > max_width and size > minimum:
        size -= 2
        font = ImageFont.truetype(font_path, size)
    return font


def make_detail_cards(font_path, product):
    """대표 일러스트를 바탕으로 상세 화면용 카드 두 장(특징 · 상품 정보)을 그린다."""
    top, bottom, accent, dark, light = catalog.PALETTES[product["color"]]
    size = seed.SIZE
    base = Image.open(os.path.join(IMG_DIR, f"{product['key']}.webp")).convert("RGB")
    cards = []
    for index in (1, 2):
        im = seed.gradient(top, bottom).convert("RGBA")
        d = ImageDraw.Draw(im)
        d.rectangle((0, 0, size, 110), fill=seed.hexrgb(dark) + (255,))
        title_font = fit_font(d, font_path, product["name"], size - 80, 46)
        d.text((40, 30), product["name"], font=title_font, fill=seed.CREAM)
        label = "작품 특징" if index == 1 else "상품 정보"
        label_font = ImageFont.truetype(font_path, 30)
        d.text((40, 140), label, font=label_font, fill=seed.hexrgb(dark))
        d.line((40, 188, size - 40, 188), fill=seed.hexrgb(accent), width=4)
        body = ImageFont.truetype(font_path, 38)
        small = ImageFont.truetype(font_path, 24)
        if index == 1:
            thumb = base.resize((300, 300))
            im.paste(thumb, (size - 340, 215))
            y = 235
            for line in product["features"]:
                d.ellipse((46, y + 14, 62, y + 30), fill=seed.hexrgb(accent) + (255,))
                text_font = fit_font(d, font_path, line, size - 420, 38, 24)
                d.text((82, y), line, font=text_font, fill=seed.hexrgb(dark))
                y += 74
            intro = product["intro"]
            lines, current = [], ""
            for ch in intro:
                if d.textlength(current + ch, font=small) > size - 80:
                    lines.append(current)
                    current = ch
                else:
                    current += ch
            lines.append(current)
            y = 560
            for text in lines[:5]:
                d.text((40, y), text, font=small, fill=seed.hexrgb(dark))
                y += 38
        else:
            rows = [("소재", product["material"]), ("제작 기간", f"약 {product['days']}일"),
                    ("가격", f"{product['price']:,}원"), ("재고", f"{product['stock']}개"),
                    ("추천 선물", " · ".join(GIFT_LABELS[t] for t in product["themes"])),
                    ("색상", " · ".join(product["colors"]))]
            y = 225
            for key, value in rows:
                d.rectangle((40, y, 200, y + 66), fill=seed.hexrgb(accent) + (255,))
                d.text((58, y + 14), key, font=ImageFont.truetype(font_path, 30), fill=seed.CREAM)
                d.rectangle((200, y, size - 40, y + 66), fill=(255, 255, 255, 215))
                d.text((222, y + 12), value, font=fit_font(d, font_path, value, size - 290, 34, 22),
                       fill=seed.hexrgb(dark))
                y += 78
            d.text((40, size - 70), "전통 공예품은 수작업이라 크기·색이 조금씩 다를 수 있습니다.", font=small,
                   fill=seed.hexrgb(dark))
        d.text((40, size - 36), "MIDAM · 시연용 일러스트", font=ImageFont.truetype(font_path, 18),
               fill=seed.hexrgb(dark))
        cards.append(im.convert("RGB"))
    return cards


def sql_text(value):
    return "'" + value.replace("'", "''") + "'"


def image_url(product):
    return f"{RAW}/{product['image']}" if product.get("image") else f"{RAW}/demo-products/{product['key']}.webp"


def detail_urls(product):
    if product.get("detail_images"):
        return [f"{RAW}/{path}" for path in product["detail_images"]]
    return [f"{RAW}/demo-products/details/{product['key']}-{n}.webp" for n in (1, 2)]


def write_images():
    font_path = next((f for f in seed.FONT_CANDIDATES if os.path.exists(f)), None)
    if not font_path:
        raise SystemExit("한글 글꼴을 찾을 수 없습니다.")
    os.makedirs(os.path.join(IMG_DIR, "details"), exist_ok=True)
    for product in catalog.PRODUCTS:
        if product.get("image"):
            continue
        make_image(font_path, product).save(os.path.join(IMG_DIR, f"{product['key']}.webp"), "WEBP", quality=88, method=6)
        for number, card in enumerate(make_detail_cards(font_path, product), start=1):
            card.save(os.path.join(IMG_DIR, "details", f"{product['key']}-{number}.webp"), "WEBP", quality=88, method=6)


def write_readme():
    lines = ["# 시연용 큐레이션 상품 (33개)", "",
             "이름·소개·특징·이미지가 서로 맞도록 `scripts/demo-products/catalog.py` 한곳에서 관리한다. 이 문서와 이미지, 시드 마이그레이션(V17)은",
             "`python3 scripts/demo-products/generate.py` 로 다시 만든다. 이미지는 직접 그린 일러스트(WebP)이며 사람·실제 사진·상표가 없다.", "",
             "| 키 | 상품명 | 장인 | 소분류 | 가격 | 선물 테마 | 특징 | 대표 | 상세 |", "|---|---|---|---|---|---|---|---|---|"]
    for p in catalog.PRODUCTS:
        lines.append(f"| {p['key']} | {p['name']} | {p['artisan']} | {p['sub']} | {p['price']:,}원 | {', '.join(p['themes'])} | "
                     f"{' / '.join(p['features'])} | [보기]({image_url(p)}) | "
                     + " ".join(f"[{n}]({u})" for n, u in enumerate(detail_urls(p), start=1)) + " |")
    lines += ["", "## 상품 소개", ""]
    for p in catalog.PRODUCTS:
        lines += [f"- **{p['name']}** — {p['intro']}"]
    with open(os.path.join(IMG_DIR, "README.md"), "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")


# 시연에서 "신상품"·"선물" 첫 화면에 먼저 보이게 할 순서(최신순 정렬이 이 순서를 따른다). 나머지는 catalog 순서.
SHOWCASE = ["p31", "p32", "p33", "p06", "p11", "p15", "p16", "p26"]


def showcase_order():
    keys = [p["key"] for p in catalog.PRODUCTS]
    ordered = SHOWCASE + [k for k in keys if k not in SHOWCASE]
    return {key: position for position, key in enumerate(ordered)}


def write_migration():
    rows, themes, tags, colors, details = [], [], [], [], []
    order = showcase_order()
    for p in catalog.PRODUCTS:
        description = p["intro"] + " 특징: " + " · ".join(p["features"]) + "."
        rows.append("    (" + ", ".join([
            sql_text(p["name"]), str(p["artisan"]), str(p["sub"]), sql_text(description), sql_text(p["material"]),
            str(p["price"]), str(p["stock"]), sql_text(image_url(p)), str(p["days"]), str(order[p["key"]])]) + ")")
        themes += [f"    ({sql_text(p['name'])}, {sql_text(t)})" for t in p["themes"]]
        tags += [f"    ({sql_text(p['name'])}, {sql_text(t)})" for t in p["tags"]]
        colors += [f"    ({sql_text(p['name'])}, {sql_text(c)})" for c in p["colors"]]
        details += [f"    ({sql_text(p['name'])}, {order}, {sql_text(url)})" for order, url in enumerate(detail_urls(p))]
    sql = f"""-- 시연용 큐레이션 상품 {len(catalog.PRODUCTS)}개 (홈 · 선물 · 상세 · 주문서 시연)
-- 원본: scripts/demo-products/catalog.py  (python3 scripts/demo-products/generate.py 로 다시 만든다)
--
-- 상품명·소개·특징·이미지가 서로 맞도록 상품마다 직접 그린 일러스트(WebP)를 저장소 외부 링크로 연결한다.
-- 서버(S3/CDN)가 꺼져 있어도 열리고 업로드 만료가 없다. 이미 같은 이름의 상품이 있으면 다시 넣지 않는다.
-- 등록 시각(created_at)을 1초씩 어긋나게 줘서 최신순(신상품·선물 첫 화면)에서 합죽선·찻잔 같은 대표 상품이 먼저 나온다.
-- 장인(artisan_id)·소분류(subcategory_id)는 시드 데이터 기준이다. 가격·재고·소개는 시연용 가짜 값이다.

-- 상세 화면 갤러리용 이미지 주소(대표 이미지 뒤에 이어 붙는다). 업로드 이미지(product_image)가 없는 상품에만 쓴다.
CREATE TABLE IF NOT EXISTS product_detail_image (
    product_id    BIGINT       NOT NULL REFERENCES product (product_id) ON DELETE CASCADE,
    display_order INT          NOT NULL,
    image_url     VARCHAR(500) NOT NULL,
    PRIMARY KEY (product_id, display_order)
);

WITH new_products AS (
    INSERT INTO product (artisan_id, category_id, subcategory_id, title, description, material, price, stock,
                         thumbnail_url, production_period_days, is_limited, is_custom_order, is_single_item,
                         has_gift_wrap, status, created_at, updated_at)
    SELECT v.artisan_id, s.category_id, v.subcategory_id, v.title, v.description, v.material, v.price, v.stock,
           v.thumbnail_url, v.days, FALSE, FALSE, FALSE, FALSE, 'ON_SALE',
           NOW() - (v.ord * INTERVAL '1 second'), NOW()
    FROM (VALUES
{(","+chr(10)).join(rows)}
    ) AS v(title, artisan_id, subcategory_id, description, material, price, stock, thumbnail_url, days, ord)
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
),
detail AS (
    INSERT INTO product_detail_image (product_id, display_order, image_url)
    SELECT n.product_id, t.display_order, t.image_url FROM new_products n
    JOIN (VALUES
{(","+chr(10)).join(details)}
    ) AS t(title, display_order, image_url) ON t.title = n.title
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
