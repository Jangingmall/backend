#!/usr/bin/env python3
"""기존 시드 상품(729개)용 소재 변형 이미지 생성기 (글자 없음).

기존 상품은 소분류(56종) 아이콘 한 장을 공유해 같은 소분류 상품이 전부 같은 그림이었다. 이를 두 단계로 나아지게 한다.
  1) 변형: (소분류, 소재 묶음)마다 상품 모양 + 소재 무늬 견본 이미지를 따로 만들고, 상세 1번에 소재 확대 이미지를 붙인다.
  2) 대표: 소분류마다 1개(가장 먼저 등록된 상품)는 상품명의 색을 읽어 색까지 맞춘 대표·소재 확대·장면 이미지를 따로 만든다.
출력: docs/demo-products/variants|materials|flagship/*.webp, docs/demo-products/variants.md,
      src/main/resources/db/migration/V19__demo_seed_product_image_variants.sql
입력: scripts/demo-products/seed_products.json (시드 상품 id·상품명·소분류·소재)
실행: python3 scripts/demo-products/variants.py   (필요: pillow)
"""
import importlib.util
import json
import math
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
DOCS = os.path.join(ROOT, "docs", "demo-products")
MIGRATION = os.path.join(ROOT, "src", "main", "resources", "db", "migration", "V19__demo_seed_product_image_variants.sql")
RAW = "https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products"


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(ROOT, path))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


catalog = load("scripts/demo-products/catalog.py", "catalog")
seed = load("scripts/seed-images/generate.py", "seed_generate")
art = load("scripts/demo-products/art.py", "art")
rgb = art.rgb
KIND = {sub: kind for sub, _name, _cat, kind in seed.ITEMS}
SUBNAME = {sub: name for sub, name, _cat, _kind in seed.ITEMS}
CATEGORY = {sub: cat for sub, _name, cat, _kind in seed.ITEMS}


# ── 소재 무늬 (art.py 에 없는 것) ──────────────────────────────────────────
def wood(d, box, light, dark, seed_=21):
    rnd = random.Random(seed_)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(light))
    for k in range(60):
        y = y0 + k * (y1 - y0) / 60
        pts = [(x, y + 9 * math.sin(x / 90 + k * 0.7) + rnd.uniform(-1.5, 1.5)) for x in range(int(x0), int(x1) + 20, 20)]
        d.line(pts, fill=rgb(dark, rnd.randint(40, 120)), width=rnd.choice([1, 2, 3]))
    for _ in range(3):  # 옹이
        cx, cy = rnd.randint(150, 650), rnd.randint(150, 650)
        for r in range(4, 46, 7):
            d.ellipse((cx - r * 1.6, cy - r, cx + r * 1.6, cy + r), outline=rgb(dark, 150), width=2)


def lacquer(d, box, light, dark, seed_=22):
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(dark))
    for k in range(-6, 14):
        d.polygon([(x0 + k * 90, y1), (x0 + k * 90 + 50, y1), (x0 + k * 90 + 330, y0), (x0 + k * 90 + 280, y0)], fill=rgb(light, 38))
    rnd = random.Random(seed_)
    for _ in range(90):
        x, y = rnd.randint(int(x0), int(x1)), rnd.randint(int(y0), int(y1))
        d.ellipse((x, y, x + 3, y + 3), fill=rgb("#e6c35a", 160))


def brass(d, box, light, dark, seed_=23):
    """방짜유기·황동: 망치 자국이 촘촘한 금속."""
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(light))
    for row, y in enumerate(range(int(y0), int(y1) + 60, 54)):
        for x in range(int(x0) - 27 * (row % 2), int(x1) + 54, 54):
            d.ellipse((x, y, x + 50, y + 50), fill=rgb(dark, 70), outline=rgb(dark, 150), width=2)
            d.arc((x + 6, y + 6, x + 44, y + 44), 200, 290, fill=rgb("#ffffff", 190), width=4)


def onggi(d, box, light, dark, seed_=24):
    rnd = random.Random(seed_)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(light))
    for _ in range(420):
        x, y, r = rnd.randint(int(x0), int(x1)), rnd.randint(int(y0), int(y1)), rnd.randint(10, 60)
        d.ellipse((x - r, y - r * 0.7, x + r, y + r * 0.7), fill=rgb(dark if rnd.random() < 0.6 else "#f0d9b0", rnd.randint(18, 48)))
    for y in range(60, 180, 14):  # 빗살 문양
        d.line((x0, y, x1, y + 6), fill=rgb(dark, 120), width=3)


def silk(d, box, light, dark, seed_=25):
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(light))
    for k in range(-10, 40):
        d.line((x0 + k * 22, y0, x0 + k * 22 + 300, y1), fill=rgb(dark, 36), width=3)
    for k in range(-4, 12):
        d.polygon([(x0 + k * 130, y1), (x0 + k * 130 + 40, y1), (x0 + k * 130 + 420, y0), (x0 + k * 130 + 380, y0)], fill=rgb("#ffffff", 70))


def bamboo(d, box, light, dark, seed_=26):
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(light))
    for k, x in enumerate(range(int(x0), int(x1), 160)):
        d.rectangle((x + 8, y0, x + 150, y1), fill=rgb(dark, 70 if k % 2 else 45), outline=rgb(dark, 170), width=3)
        d.rectangle((x + 20, y0, x + 40, y1), fill=rgb("#ffffff", 80))
        for y in range(int(y0) + 110 + 37 * (k % 3), int(y1), 230):
            d.rectangle((x + 4, y, x + 154, y + 14), fill=rgb(dark, 190))


def leather(d, box, light, dark, seed_=27):
    rnd = random.Random(seed_)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(light))
    for _ in range(4200):
        x, y, r = rnd.randint(int(x0), int(x1)), rnd.randint(int(y0), int(y1)), rnd.randint(3, 8)
        d.ellipse((x - r, y - r, x + r, y + r), outline=rgb(dark, rnd.randint(40, 110)), width=1)
    for x in range(int(x0) + 20, int(x1), 26):  # 박음질
        d.line((x, 380, x + 14, 380), fill=rgb("#f3e5c4", 230), width=5)


def tea(d, box, light, dark, seed_=28):
    rnd = random.Random(seed_)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(dark))
    for _ in range(170):
        cx, cy, a, l = rnd.randint(0, 800), rnd.randint(0, 800), rnd.uniform(0, math.pi), rnd.randint(50, 100)
        shade = rnd.choice([light, "#5f8f4e", "#3f6f3a"])
        pts = []
        for t in range(0, 21):
            u = t / 20
            w = math.sin(u * math.pi) * l * 0.22
            pts.append((cx + math.cos(a) * l * u - math.sin(a) * w, cy + math.sin(a) * l * u + math.cos(a) * w))
        for t in range(20, -1, -1):
            u = t / 20
            w = -math.sin(u * math.pi) * l * 0.22
            pts.append((cx + math.cos(a) * l * u - math.sin(a) * w, cy + math.sin(a) * l * u + math.cos(a) * w))
        d.polygon(pts, fill=rgb(shade, 235), outline=rgb(dark, 160))
        d.line((cx, cy, cx + math.cos(a) * l, cy + math.sin(a) * l), fill=rgb(dark, 130), width=2)


def paste(d, box, light, dark, seed_=29):
    """장·김치·고춧가루: 붉고 짙은 양념의 알갱이."""
    rnd = random.Random(seed_)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(light))
    for _ in range(300):
        x, y, r = rnd.randint(0, 800), rnd.randint(0, 800), rnd.randint(14, 70)
        d.ellipse((x - r, y - r * 0.8, x + r, y + r * 0.8), fill=rgb(dark, rnd.randint(20, 60)))
    for _ in range(1500):
        x, y = rnd.randint(0, 800), rnd.randint(0, 800)
        l = rnd.randint(5, 14)
        a = rnd.uniform(0, math.pi)
        d.line((x, y, x + l * math.cos(a), y + l * math.sin(a)), fill=rgb(rnd.choice(["#b22a1e", "#d4452b", "#7a1a12", "#e87b4a"]), 230), width=rnd.randint(3, 6))
    for _ in range(90):
        x, y = rnd.randint(0, 800), rnd.randint(0, 800)
        d.ellipse((x, y, x + 5, y + 5), fill=rgb("#f6e9c6"))


def rice(d, box, light, dark, seed_=30):
    """떡·한과·곡물: 하얀 쌀알과 깨."""
    rnd = random.Random(seed_)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(light))
    for _ in range(1300):
        x, y = rnd.randint(0, 800), rnd.randint(0, 800)
        a = rnd.uniform(0, math.pi)
        dx, dy = math.cos(a) * 20, math.sin(a) * 20
        d.line((x, y, x + dx, y + dy), fill=rgb(rnd.choice(["#fffdf6", "#f6efdc", "#ede1c3"])), width=11)
        d.line((x + 2, y + 2, x + dx * 0.8, y + dy * 0.8), fill=rgb(dark, 55), width=1)
    for _ in range(160):
        x, y = rnd.randint(0, 800), rnd.randint(0, 800)
        d.ellipse((x, y, x + 6, y + 9), fill=rgb("#3a2c22"))


def porcelain(d, box, light, dark, seed_=31):
    """백자: 맑은 흰 유약과 청화 붓 무늬."""
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(light))
    for k in range(7):
        y = 90 + k * 105
        pts = [(x, y + 26 * math.sin(x / 60 + k)) for x in range(0, 821, 20)]
        d.line(pts, fill=rgb(dark, 150), width=7)
    for x in range(70, 800, 170):
        art.flower(d, x, 400 + 120 * math.sin(x / 90), 34, "#ffffff", center=dark, edge=dark)


def glaze(d, box, light, dark):
    art.crackle(d, box, light)


def rim(d, box, light, dark):
    art.weave(d, box, light, dark)


# 소재 묶음: 이름 → (한글 이름, 팔레트(위·아래·포인트·진한·밝은), 그리는 함수)
CLASSES = {
    "lacquer": ("옻칠", ("#f3ece6", "#dccdc4", "#8a2a1f", "#2a1410", "#a8453a"), lacquer),
    "najeon": ("나전·자개", ("#eef5f3", "#cfe1dc", "#4f8f9c", "#1f4650", "#cfe9ee"), lambda d, b, l, k: art.shell(d, b)),
    "enamel": ("칠보", ("#eceff6", "#cdd5e6", "#2b3f7a", "#12204a", "#c3cde8"), lambda d, b, l, k: art.enamel(d, b, l, ["#2b3f7a", "#12204a", "#2f8f8a", "#c0392b"])),
    "brass": ("방짜유기·황동", ("#faf3df", "#ecdca8", "#c9a227", "#5d4708", "#e0b84a"), brass),
    "silver": ("은·백동", ("#f1f3f5", "#d5dbe0", "#8d99a6", "#3a4552", "#dfe5ea"), lambda d, b, l, k: art.silver(d, b, l, k)),
    "jade": ("옥·보석", ("#eef7f2", "#cfe6da", "#3f9a78", "#154a36", "#bfe8d3"), lambda d, b, l, k: art.jade(d, b, "#3f9a78")),
    "celadon": ("청자·분청", ("#ecf5f4", "#cfe4e1", "#2f8f8a", "#134a47", "#bfe3df"), glaze),
    "porcelain": ("백자·도자기", ("#f6f7f8", "#dfe3e6", "#3b5a99", "#1d2f5a", "#f4f6f8"), porcelain),
    "onggi": ("옹기·흙", ("#f4eadb", "#dcc7a4", "#8a5a30", "#4a2c14", "#b98a5a"), onggi),
    "silk": ("비단·명주", ("#f7eef5", "#e6d2e1", "#9b4f86", "#4f1f48", "#e8c4de"), silk),
    "hanji": ("한지", ("#faf6ea", "#e9e0c6", "#b9a46a", "#4d4220", "#f3ecd3"), lambda d, b, l, k: art.hanji(d, b, l, k)),
    "bamboo": ("대나무", ("#eff5ec", "#d5e3cf", "#5f8f4e", "#2c4a22", "#cfe6c3"), bamboo),
    "leather": ("가죽·모피", ("#f4ece2", "#dbc6ab", "#8a5a30", "#3a2410", "#a8784a"), leather),
    "wood": ("목재", ("#f4eee3", "#dccdb0", "#a8763e", "#5e3b1c", "#e8cfa5"), wood),
    "textile": ("면·모시·삼베", ("#f4f0e6", "#ded4bd", "#a38b5c", "#4a3b1f", "#e6dbbd"), lambda d, b, l, k: art.weave(d, b, l, k)),
    "paste": ("장·김치", ("#fbeee6", "#ecc9b4", "#b22a1e", "#5a1a10", "#f1d4b8"), paste),
    "tea": ("차", ("#eff5ec", "#cfe3c8", "#3f7f3a", "#1f4a24", "#a9d27a"), tea),
    "rice": ("떡·한과·곡물", ("#faf6ea", "#ecdfbf", "#b9923a", "#6b4a14", "#fdf8ea"), rice),
}
TOKEN_RULES = [  # (묶음, 포함하는 낱말) — 소재의 첫 낱말부터 이 순서로 맞춰 본다
    ("lacquer", ["옻칠"]), ("najeon", ["나전", "자개"]), ("enamel", ["칠보"]),
    ("brass", ["유기", "놋쇠", "황동", "신주", "두석", "금도금", "금속", "무쇠", "쇠", "철", "스테인리스"]),
    ("silver", ["순은", "백동", "은사", "은침", "은상감"]), ("jade", ["옥", "비취", "산호", "호박", "자수정", "원석", "석"]),
    ("celadon", ["청자", "분청", "자기"]), ("porcelain", ["백자", "도자기", "세라믹"]),
    ("onggi", ["옹기", "흙"]), ("silk", ["비단", "명주", "실크", "양단", "오간자", "금사", "솜", "금박"]),
    ("hanji", ["한지", "닥종이", "종이", "PP"]), ("bamboo", ["대나무", "오죽", "시누대", "물뿔", "대올", "대"]),
    ("leather", ["가죽", "우피", "털", "말총", "인모", "마이크로"]),
    ("wood", ["원목", "나무", "목재", "느티", "오동", "박달", "소나무", "물푸레", "캄포", "유자목", "은행", "먹감", "고급 원목"]),
    ("textile", ["모시", "삼베", "면", "리넨", "울", "모직", "양모", "마", "짚", "왕골", "실", "아크릴", "캔버스"]),
    ("paste", ["간장", "고추장", "된장", "고춧가루", "액젓", "김치", "배추", "무", "열무", "갓", "쪽파", "오이", "부추", "마늘", "깻잎", "우엉", "도라지", "대두", "천일염", "감자", "단감", "매실", "굴비", "알타리", "식품"]),
    ("tea", ["차", "녹차", "잎", "국화", "생강나무꽃", "산수유", "황차", "수국"]),
    ("rice", ["찹쌀", "쌀", "조청", "엿", "튀밥", "백미", "밀가루", "메밀", "수수", "견과", "아몬드", "솔잎", "꿀", "누룩", "흑임자", "소곡주", "한과", "조"]),
]
OVERRIDE = [("lacquer", "옻칠"), ("najeon", "나전"), ("najeon", "자개"), ("enamel", "칠보")]


def classify(material):
    for name, word in OVERRIDE:
        if word in material:
            return name
    for token in material.split():
        for name, words in TOKEN_RULES:
            if any((token == w) if len(w) == 1 else (w in token) for w in words):
                return name
    return "textile"


# 상품명 색 낱말 → (색 이름, 색 견본, 팔레트 이름)
TITLE_COLORS = [("청자", "비색", "#9bc9b8", "teal"), ("분청", "회청색", "#b9c4b4", "natural"), ("백자", "흰색", "#f4f6f8", "white"),
                ("청화", "쪽빛", "#2d4f94", "indigo"), ("쪽빛", "쪽빛", "#2d4f94", "indigo"), ("남색", "남색", "#1f2f6b", "navy"),
                ("분홍", "분홍", "#ee8fae", "rose"), ("홍", "홍색", "#c0392b", "rose"), ("자주", "자주", "#8f3a77", "plum"),
                ("연두", "연두", "#a9d27a", "green"), ("녹", "녹색", "#4f8f45", "green"), ("옥", "옥색", "#8fd0b5", "jade"),
                ("금", "금색", "#d4af37", "gold"), ("황", "황색", "#d9902b", "amber"), ("하늘", "하늘색", "#8cc4ec", "sky"),
                ("먹", "먹색", "#1a1a1a", "ink"), ("흑", "검정", "#1a1a1a", "ink"), ("흰", "흰색", "#fafafa", "white"),
                ("백", "흰색", "#fafafa", "white"), ("은", "은색", "#c4ccd4", "silver")]


NOT_COLOR = ("옥식기", "옥접시", "옥관자", "옥춘", "백미", "백동", "백옥", "금박", "금도금", "먹감", "금속", "황동")


def title_color(title):
    """상품명에서 색 낱말을 찾는다. 장인 이름(앞의 세 글자 낱말)과 색이 아닌 낱말(옥식기·백미 …)은 건너뛴다."""
    tokens = title.split()
    if len(tokens) >= 3 and len(tokens[0]) == 3:
        tokens = tokens[1:]
    tokens = [t for t in tokens if not t.startswith(NOT_COLOR)]
    text = " ".join(tokens)
    for word, name, hexv, palette in TITLE_COLORS:
        if (len(word) > 1 and word in text) or (len(word) == 1 and any(t.startswith(word) for t in tokens)):
            return name, hexv, palette
    return None


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.convert("RGB").save(path, "WEBP", quality=86, method=6)


def macro(cls):
    palette = CLASSES[cls][1]
    im = Image.new("RGBA", (800, 800), seed.hexrgb(palette[0]) + (255,))
    CLASSES[cls][2](ImageDraw.Draw(im), (0, 0, 800, 800), palette[4], palette[3])
    return im.convert("RGB")


def with_swatch(base, swatch_img, accent):
    base = base.convert("RGBA")
    swatch = swatch_img.crop((150, 150, 650, 650)).resize((200, 200), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", (800, 800), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, 799, 799), fill=255)
    mask = mask.resize((200, 200), Image.LANCZOS)
    d = ImageDraw.Draw(base)
    d.ellipse((582, 582, 808, 808), fill=(255, 255, 255, 255))
    d.ellipse((582, 582, 808, 808), outline=seed.hexrgb(accent) + (255,), width=3)
    base.paste(swatch, (590, 590), mask)
    return base.convert("RGB")


def scene(kind, palette, dots):
    top, bottom, accent, dark, light = palette
    im = seed.gradient(top, bottom).convert("RGBA")
    d = ImageDraw.Draw(im)
    d.rectangle((0, 560, 800, 800), fill=seed.hexrgb(bottom) + (255,))
    d.rectangle((0, 560, 800, 566), fill=seed.hexrgb(accent) + (255,))
    shadow = Image.new("RGBA", (800, 800), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).ellipse((200, 590, 600, 650), fill=(0, 0, 0, 55))
    im.alpha_composite(shadow)
    obj = seed.render(kind, palette, background=False).crop((100, 130, 700, 650)).resize((540, 468), Image.LANCZOS)
    im.alpha_composite(obj, (130, 110))
    d = ImageDraw.Draw(im)
    for n, value in enumerate(dots[:6]):
        x = 60 + n * 74
        d.ellipse((x, 60, x + 56, 116), fill=(255, 255, 255, 255))
        d.ellipse((x + 5, 65, x + 51, 111), fill=rgb(value))
    return im.convert("RGB")


def sql_text(value):
    return "'" + value.replace("'", "''") + "'"


def main():
    products = json.load(open(os.path.join(os.path.dirname(__file__), "seed_products.json"), encoding="utf-8"))
    for p in products:
        p["cls"] = classify(p["material"])
    # 1) 소재 확대 이미지
    used = sorted({p["cls"] for p in products})
    macros = {cls: macro(cls) for cls in used}
    for cls, im in macros.items():
        save(im, os.path.join(DOCS, "materials", f"{cls}.webp"))
    # 대표(소분류마다 먼저 등록된 1개)
    flagship = {}
    for p in sorted(products, key=lambda x: x["id"]):
        flagship.setdefault(p["sub"], p)
    flag_ids = {p["id"] for p in flagship.values()}
    # 2) (소분류, 묶음) 변형 대표 이미지
    combos = sorted({(p["sub"], p["cls"]) for p in products if p["id"] not in flag_ids})
    for sub, cls in combos:
        palette = CLASSES[cls][1]
        main_img = with_swatch(seed.render(KIND[sub], palette), macros[cls], palette[2])
        save(main_img, os.path.join(DOCS, "variants", f"s{sub:02d}-{cls}.webp"))
    # 3) 대표 상품 이미지(상품명 색 반영)
    flag_rows = []
    for sub, p in sorted(flagship.items()):
        cls = p["cls"]
        color = title_color(p["title"])
        palette = catalog.PALETTES[color[2]] if color else CLASSES[cls][1]
        key = f"f{sub:02d}"
        main_img = with_swatch(seed.render(KIND[sub], palette), macros[cls], palette[2])
        save(main_img, os.path.join(DOCS, "flagship", f"{key}.webp"))
        dots = [color[1]] if color else [CLASSES[cls][1][2]]
        save(scene(KIND[sub], palette, dots), os.path.join(DOCS, "flagship", f"{key}-2.webp"))
        flag_rows.append((p, key, cls, color))
    write_docs(products, combos, flag_rows)
    write_migration(products, combos, flag_rows, flag_ids)
    print(f"소재 묶음 {len(used)}, 변형 {len(combos)}, 대표 {len(flag_rows)} -> V19 생성")


def write_docs(products, combos, flag_rows):
    lines = ["# 기존 시드 상품 이미지 변형 (글자 없음)", "",
             "기존 시드 상품 729개는 소분류 아이콘 한 장을 공유했다. `scripts/demo-products/variants.py` 가 (소분류, 소재 묶음)마다",
             "상품 모양 + 소재 무늬 견본 이미지를 만들고, 상세 1번에 소재 확대 이미지를 붙이며, 소분류마다 1개 상품은 상품명의 색까지 맞춘다.", "",
             "## 소재 묶음 (상세 1번: 소재 확대)", "", "| 묶음 | 이름 | 이미지 | 상품 수 |", "|---|---|---|---|"]
    counts = {}
    for p in products:
        counts[p["cls"]] = counts.get(p["cls"], 0) + 1
    for cls, n in sorted(counts.items(), key=lambda x: -x[1]):
        lines.append(f"| {cls} | {CLASSES[cls][0]} | [보기]({RAW}/materials/{cls}.webp) | {n} |")
    lines += ["", f"## 대표 상품 {len(flag_rows)}개 (소분류마다 먼저 등록된 상품, 상품명 색 반영)", "",
              "| 키 | 상품 | 소분류 | 소재 묶음 | 색 | 대표 | 상세1 | 상세2 |", "|---|---|---|---|---|---|---|---|"]
    for p, key, cls, color in flag_rows:
        lines.append(f"| {key} | {p['title']} | {SUBNAME[p['sub']]} | {CLASSES[cls][0]} | {color[0] if color else '-'} | "
                     f"[보기]({RAW}/flagship/{key}.webp) | [보기]({RAW}/materials/{cls}.webp) | [보기]({RAW}/flagship/{key}-2.webp) |")
    lines += ["", f"변형 대표 이미지 {len(combos)}개는 `docs/demo-products/variants/s<소분류>-<묶음>.webp` 이다."]
    with open(os.path.join(DOCS, "variants.md"), "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")


def write_migration(products, combos, flag_rows, flag_ids):
    flag_vals = ",\n".join(
        f"    ({sql_text(p['title'])}, {p['sub']}, {sql_text(f'{RAW}/flagship/{key}.webp')}, "
        f"{sql_text(f'{RAW}/materials/{cls}.webp')}, {sql_text(f'{RAW}/flagship/{key}-2.webp')}, "
        f"{sql_text(color[0]) if color else 'NULL'})" for p, key, cls, color in flag_rows)
    combo_vals = ",\n".join(
        f"    ({sub}, {sql_text(mat)}, {sql_text(f'{RAW}/variants/s{sub:02d}-{cls}.webp')}, {sql_text(f'{RAW}/materials/{cls}.webp')})"
        for sub, mat, cls in sorted({(p['sub'], p['material'], p['cls']) for p in products if p['id'] not in flag_ids}))
    sql = f"""-- 기존 시드 상품(소분류 아이콘 공유)의 이미지를 소재·소분류별 변형 이미지로 바꾼다 (글자 없음, 저장소 외부 링크).
-- 원본: scripts/demo-products/variants.py  (python3 scripts/demo-products/variants.py 로 다시 만든다)
-- 대상: thumbnail_url 이 아직 소분류 아이콘(docs/seed-images)인 상품만. 큐레이션 상품(V17)과 업로드 이미지 상품은 건드리지 않는다.
--  1) 대표 상품(소분류마다 가장 먼저 등록된 상품): 상품명 색을 반영한 대표 이미지 + 상세(소재 확대, 장면) + 색상
--  2) 나머지: (소분류, 소재)별 변형 대표 이미지 + 상세(소재 확대)

CREATE TEMP TABLE demo_flagship (title VARCHAR(200), subcategory_id BIGINT, main_url VARCHAR(500),
    macro_url VARCHAR(500), scene_url VARCHAR(500), color VARCHAR(20)) ON COMMIT DROP;
INSERT INTO demo_flagship VALUES
{flag_vals};

CREATE TEMP TABLE demo_variant (subcategory_id BIGINT, material VARCHAR(100), main_url VARCHAR(500),
    macro_url VARCHAR(500)) ON COMMIT DROP;
INSERT INTO demo_variant VALUES
{combo_vals};

-- 상세 이미지 (대표 이미지를 바꾸기 전에 넣는다: 아직 아이콘인 상품을 찾기 위해)
INSERT INTO product_detail_image (product_id, display_order, image_url)
SELECT p.product_id, d.ord, d.url FROM product p
JOIN demo_flagship f ON f.title = p.title AND f.subcategory_id = p.subcategory_id
CROSS JOIN LATERAL (VALUES (0, f.macro_url), (1, f.scene_url)) AS d(ord, url)
WHERE p.thumbnail_url LIKE '%/docs/seed-images/%'
ON CONFLICT DO NOTHING;

INSERT INTO product_color (product_id, color)
SELECT p.product_id, f.color FROM product p
JOIN demo_flagship f ON f.title = p.title AND f.subcategory_id = p.subcategory_id
WHERE p.thumbnail_url LIKE '%/docs/seed-images/%' AND f.color IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM product_color c WHERE c.product_id = p.product_id);

INSERT INTO product_detail_image (product_id, display_order, image_url)
SELECT p.product_id, 0, v.macro_url FROM product p
JOIN demo_variant v ON v.subcategory_id = p.subcategory_id AND v.material = COALESCE(p.material, '')
WHERE p.thumbnail_url LIKE '%/docs/seed-images/%'
  AND NOT EXISTS (SELECT 1 FROM product_detail_image x WHERE x.product_id = p.product_id)
ON CONFLICT DO NOTHING;

-- 대표 이미지 바꾸기 (대표 상품 먼저, 그다음 나머지)
UPDATE product p SET thumbnail_url = f.main_url
FROM demo_flagship f
WHERE f.title = p.title AND f.subcategory_id = p.subcategory_id AND p.thumbnail_url LIKE '%/docs/seed-images/%';

UPDATE product p SET thumbnail_url = v.main_url
FROM demo_variant v
WHERE v.subcategory_id = p.subcategory_id AND v.material = COALESCE(p.material, '')
  AND p.thumbnail_url LIKE '%/docs/seed-images/%';
"""
    with open(MIGRATION, "w", encoding="utf-8") as handle:
        handle.write(sql)


if __name__ == "__main__":
    main()
