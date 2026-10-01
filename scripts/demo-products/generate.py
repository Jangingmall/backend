#!/usr/bin/env python3
"""시연용 큐레이션 상품 생성기 (catalog.py 가 원본).

만드는 것
  1) docs/demo-products/<key>.webp   상품별 일러스트(800×800 WebP, 사람·실제 사진·상표 없음)
  2) docs/demo-products/README.md    상품 표(이름·소개·특징·가격·선물 테마·이미지 주소)
  3) src/main/resources/db/migration/V17__demo_curated_products.sql   시드 마이그레이션

필요: pip install pillow. 이미지에는 글자가 없다(설명은 텍스트로 따로).
실행: python3 scripts/demo-products/generate.py
"""
import importlib.util
import os

from PIL import Image, ImageDraw

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


art = load("scripts/demo-products/art.py", "art")

# 상품 → 소재·무늬(art.py). 이미지로 소재·색·무늬를 보여 주고, 설명 글은 DB 텍스트로 따로 둔다.
TEXTURES = {
    "weave": ["p01", "p05", "p14", "p16", "p17", "p18", "p19", "p20"],
    "quilt": ["p02", "p13"], "patchwork": ["p04", "p08"], "plum": ["p03", "p31"], "peony": ["p11"],
    "shell": ["p26", "p27"], "knot": ["p06", "p07", "p21", "p22"], "enamel": ["p23", "p28"],
    "silver": ["p09", "p24"], "jade": ["p10"], "pearls": ["p25"], "hanji": ["p15", "p30"],
    "landscape": ["p12"], "ink": ["p29"], "crackle": ["p32"], "gwiyal": ["p33"],
}
TEXTURE_OF = {key: texture for texture, keys in TEXTURES.items() for key in keys}

# 색 이름 → 색 견본(상세 2번째 이미지의 색 점)
COLOR_HEX = {
    "청록": "#1f8a86", "쪽빛": "#2d4f94", "연두": "#a9d27a", "은색": "#c4ccd4", "옥색": "#8fd0b5", "아이보리": "#f3ecd3",
    "흰색": "#fafafa", "연분홍": "#f6c9d6", "자주": "#8f3a77", "홍색": "#c0392b", "분홍": "#ee8fae", "녹색": "#4f8f45",
    "황토": "#b9873f", "호박색": "#d9902b", "하늘색": "#8cc4ec", "자연색": "#d8c9a3", "민트": "#8fe0bb", "푸른색": "#3f78c4",
    "오방색": ["#2d4f94", "#d9b23a", "#c0392b", "#fafafa", "#1a1a1a"],
    "금색": "#d4af37", "진주색": "#f1e8dc", "남색": "#1f2f6b", "먹색": "#1a1a1a", "비색": "#9bc9b8", "회백색": "#d9d6cf",
}
OBJECT_PRODUCTS = {"p31", "p32", "p33"}


def macro(product):
    """상세 1번: 소재·무늬 확대(글자 없음)."""
    texture = TEXTURE_OF[product["key"]]
    palette = catalog.PALETTES[product["color"]]
    im = Image.new("RGBA", (seed.SIZE, seed.SIZE), seed.hexrgb(palette[0]) + (255,))
    art.paint(texture, ImageDraw.Draw(im), (0, 0, seed.SIZE, seed.SIZE), palette, {"colors": None})
    return im.convert("RGB")


def make_image(product):
    """대표 이미지: 상품 모양 일러스트 + 같은 소재의 동그란 무늬 견본(글자 없음)."""
    palette = catalog.PALETTES[product["color"]]
    im = seed.render(product["kind"], palette).convert("RGBA")
    swatch = macro(product).crop((150, 150, 650, 650)).resize((200, 200), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", (800, 800), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, 799, 799), fill=255)
    mask = mask.resize((200, 200), Image.LANCZOS)
    cx, cy = 590, 590
    ring = ImageDraw.Draw(im)
    ring.ellipse((cx - 8, cy - 8, cx + 208, cy + 208), fill=(255, 255, 255, 255))
    ring.ellipse((cx - 8, cy - 8, cx + 208, cy + 208), outline=seed.hexrgb(palette[2]) + (255,), width=3)
    im.paste(swatch, (cx, cy), mask)
    return im.convert("RGB")


def make_scene(product):
    """상세 2번: 상품을 작업대 위에 놓은 장면 + 색 견본 점(글자 없음)."""
    top, bottom, accent, dark, light = catalog.PALETTES[product["color"]]
    size = seed.SIZE
    im = seed.gradient(top, bottom).convert("RGBA")
    d = ImageDraw.Draw(im)
    d.rectangle((0, 560, size, size), fill=seed.hexrgb(bottom) + (255,))
    d.rectangle((0, 560, size, 566), fill=seed.hexrgb(accent) + (255,))
    shadow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse((200, 590, 600, 650), fill=(0, 0, 0, 55))
    im.alpha_composite(shadow)
    obj = seed.render(product["kind"], catalog.PALETTES[product["color"]], background=False)
    obj = obj.crop((100, 130, 700, 650)).resize((540, 468), Image.LANCZOS)
    im.alpha_composite(obj, (130, 110))
    d = ImageDraw.Draw(im)
    swatches = []
    for name in product["colors"]:
        value = COLOR_HEX[name]
        swatches += value if isinstance(value, list) else [value]
    for n, value in enumerate(swatches[:6]):
        x = 60 + n * 74
        d.ellipse((x, 60, x + 56, 116), fill=(255, 255, 255, 255))
        d.ellipse((x + 5, 65, x + 51, 111), fill=art.rgb(value))
    return im.convert("RGB")


def sql_text(value):
    return "'" + value.replace("'", "''") + "'"


def image_url(product):
    return f"{RAW}/{product['image']}" if product.get("image") else f"{RAW}/demo-products/{product['key']}.webp"


def detail_urls(product):
    """상세 갤러리: 소재 확대, 장면(또는 이미 만든 시연 일러스트) 순서."""
    key = product["key"]
    if key in OBJECT_PRODUCTS:
        extras = [f"{RAW}/{path}" for path in product["detail_images"]]
    else:
        extras = [f"{RAW}/demo-products/details/{key}-2.webp"]
    return [f"{RAW}/demo-products/details/{key}-1.webp"] + extras


def write_images():
    os.makedirs(os.path.join(IMG_DIR, "details"), exist_ok=True)
    save = dict(format="WEBP", quality=88, method=6)
    for product in catalog.PRODUCTS:
        key = product["key"]
        if not product.get("image"):
            make_image(product).save(os.path.join(IMG_DIR, f"{key}.webp"), **save)
        macro(product).save(os.path.join(IMG_DIR, "details", f"{key}-1.webp"), **save)
        if key not in OBJECT_PRODUCTS:
            make_scene(product).save(os.path.join(IMG_DIR, "details", f"{key}-2.webp"), **save)
        else:
            stale = os.path.join(IMG_DIR, "details", f"{key}-2.webp")
            if os.path.exists(stale):
                os.remove(stale)


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
