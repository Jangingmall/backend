#!/usr/bin/env python3
"""큐레이션 상품 33개의 상세 소개(본문 이미지 여러 장 + 소제목·본문)를 content·content_block 으로 심는 V22 마이그레이션을 만든다.

- 전주 합죽선(p31): AI 상세 페이지 결과(사진 9장, 섹션 9개)를 그대로 쓰고 react_document 도 저장한다.
- 나머지 32개: 상품마다 실사 A·B·C 이미지를 본문에 넣고, 제작 이야기·소재와 결·쓰임·특징·관리 안내를 소재·소분류에 맞춰 쓴다.
소비자 상세는 content_block(h2·p·img)을 순서대로 보여 준다.
실행: python3 scripts/demo-products/seed_detail_content.py
"""
import importlib.util
import json
import os
import re

HERE = os.path.dirname(__file__)
ROOT = os.path.join(HERE, "..", "..")
SRC = os.path.join(ROOT, "docs", "demo-products", "detail", "p31")
README = os.path.join(ROOT, "docs", "demo-products", "README.md")
V17 = os.path.join(ROOT, "src", "main", "resources", "db", "migration", "V17__demo_curated_products.sql")
OUT = os.path.join(ROOT, "src", "main", "resources", "db", "migration", "V22__demo_detail_content.sql")
RAW = "https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products"
P31 = ("전주 합죽선 · 매화선", 41)

spec = importlib.util.spec_from_file_location("rename_products", os.path.join(HERE, "rename_products.py"))
RP = importlib.util.module_from_spec(spec)
spec.loader.exec_module(RP)

P31_ALT = {  # react 문서 img 노드 → 사진
    "section-01-hero-image-01": "01-hero", "section-03-detail_split-image-01": "03-detail", "section-04-palette-image-01": "06-detail-02",
    "section-05-usage_scene-image-01": "04-lifestyle", "section-06-gallery-image-01": "05-lifestyle-02", "section-06-gallery-image-02": "07-detail-03",
}
P31_PHOTOS = {"hero": ["01-hero"], "statement": ["02-packshot"], "detail_split": ["03-detail"], "palette": ["06-detail-02"], "usage_scene": ["04-lifestyle"],
              "gallery": ["05-lifestyle-02", "07-detail-03", "08-detail-04", "09-detail-05"]}


def q(s):
    return "'" + s.replace("'", "''") + "'"


def curated():
    """README(키·이름·장인·소분류·특징) + V17(설명·소재)."""
    desc = {}
    for m in re.finditer(r"^    \('((?:[^']|'')*)', (\d+), (\d+), '((?:[^']|'')*)', '((?:[^']|'')*)', (\d+),", open(V17, encoding="utf-8").read(), re.M):
        desc[(m.group(1).replace("''", "'"), int(m.group(2)))] = (m.group(4).replace("''", "'"), m.group(5))
    out = []
    for line in open(README, encoding="utf-8"):
        m = re.match(r"\| (p\d\d) \| ([^|]+?) \| (\d+) \| (\d+) \| [^|]+ \| [^|]+ \| ([^|]+?) \|", line)
        if m:
            key, title, artisan, sub, feats = m.group(1), m.group(2).strip(), int(m.group(3)), int(m.group(4)), m.group(5).strip()
            d, material = desc[(title, artisan)]
            out.append({"key": key, "title": title, "artisan": artisan, "sub": sub, "features": [f.strip() for f in feats.split("/")],
                        "description": d.split(" 특징:")[0], "material": material, "cls": RP.V.classify(material)})
    return out


def p31_blocks():
    plan = json.load(open(os.path.join(SRC, "page_plan.json"), encoding="utf-8"))
    out = []
    for s in plan:
        for photo in P31_PHOTOS.get(s["block_type"], []):
            out.append(("img", None, f"{RAW}/detail/p31/{photo}.webp"))
        out.append(("h2", s["title"], None))
        text = s["body"]
        for item in s.get("items") or []:
            text += f"\n· {item['label']} — {item['value']}. {item['description']}"
        out.append(("p", text[:2000], None))
    return out


def product_blocks(p):
    img = lambda cut: ("img", None, f"{RAW}/photoreal/staging/{p['key']}_{cut}.webp")
    item = RP.ITEM[p["sub"]]
    rid = int(p["key"][1:])
    tech = RP.TECH[p["cls"]][rid % 3].format(item=item)
    care = RP.CARE[p["cls"]]
    use = RP.USE[p["sub"]]
    feats = "\n".join(f"· {f}" for f in p["features"])
    return [
        img("A"), ("h2", p["title"], None), ("p", p["description"], None),
        ("h2", "만든 이야기", None), ("p", f"{tech} 한 점씩 손으로 만들어 같은 모양이 하나도 없고, 만든 이의 손길이 그대로 남아 있습니다.", None),
        img("B"), ("h2", "소재와 결", None), ("p", f"{p['material']}의 결을 살려 마감했습니다. 가까이에서 보면 손으로 다듬은 흔적과 소재 고유의 질감이 또렷하게 보입니다.", None),
        img("C"), ("h2", "이렇게 쓰세요", None), ("p", f"{use[0]} {use[1]}", None),
        ("h2", "특징", None), ("p", feats, None),
        ("h2", "관리 안내", None), ("p", f"{care[0]} {care[1]}", None),
        ("h2", "한 점의 차이", None), ("p", f"손으로 만든 {item}이라 사진과 결·색이 조금씩 다를 수 있습니다. 그 차이가 이 작품만의 멋입니다.", None),
    ]


def react_document():
    doc = json.load(open(os.path.join(SRC, "react_document.json"), encoding="utf-8"))

    def walk(node):
        if isinstance(node, dict):
            if node.get("tag") == "img" and node.get("id") in P31_ALT:
                node.setdefault("props", {})["src"] = f"{RAW}/detail/p31/{P31_ALT[node['id']]}.webp"
            for child in node.get("children") or []:
                walk(child)
    for root in doc["root"]:
        walk(root)
    return json.dumps(doc, ensure_ascii=False, separators=(",", ":"))


def main():
    rows = []
    for p in curated():
        blocks = p31_blocks() if (p["title"], p["artisan"]) == P31 else product_blocks(p)
        for n, (tag, text, url) in enumerate(blocks, 1):
            rows.append(f"    ({q(p['title'])}, {p['artisan']}, {n}, {q(tag)}, {q(text) if text else 'NULL'}, {q(url) if url else 'NULL'})")
    values = ",\n".join(rows)
    text = f"""-- 큐레이션 상품 33개의 상세 소개(본문 이미지 여러 장 + 소제목·본문)를 심는다. 원본: scripts/demo-products/seed_detail_content.py
-- 소비자 상세는 content_block 을 순서대로 보여 준다. 전주 합죽선은 react_document 도 저장해 판매자 미리보기·편집에서 같은 내용이 열린다.

CREATE TEMP TABLE demo_detail_blocks (title VARCHAR(200), artisan_id BIGINT, display_order SMALLINT, tag VARCHAR(10),
    body VARCHAR(2000), image_url VARCHAR(500)) ON COMMIT DROP;
INSERT INTO demo_detail_blocks VALUES
{values};

INSERT INTO content (product_id, status, version, fact_check_confirmed, photo_match_confirmed, display_approval_badge, created_at, updated_at)
SELECT p.product_id, 'PUBLISHED', 0, TRUE, TRUE, TRUE, NOW(), NOW()
FROM product p JOIN (SELECT DISTINCT title, artisan_id FROM demo_detail_blocks) d ON d.title = p.title AND d.artisan_id = p.artisan_id
ON CONFLICT (product_id) DO UPDATE SET status = 'PUBLISHED', fact_check_confirmed = TRUE, photo_match_confirmed = TRUE,
    display_approval_badge = TRUE, updated_at = NOW();

UPDATE content c SET react_document = {q(react_document())}
FROM product p WHERE c.product_id = p.product_id AND p.title = {q(P31[0])} AND p.artisan_id = {P31[1]};

DELETE FROM content_block b USING content c JOIN product p ON p.product_id = c.product_id
JOIN (SELECT DISTINCT title, artisan_id FROM demo_detail_blocks) d ON d.title = p.title AND d.artisan_id = p.artisan_id
WHERE b.content_id = c.content_id;

INSERT INTO content_block (content_id, display_order, tag, text, image_url)
SELECT c.content_id, d.display_order, d.tag, d.body, d.image_url
FROM demo_detail_blocks d JOIN product p ON p.title = d.title AND p.artisan_id = d.artisan_id
JOIN content c ON c.product_id = p.product_id;
"""
    open(OUT, "w", encoding="utf-8").write(text)
    print(len(rows), "blocks ->", OUT, len(text) // 1024, "KB")


if __name__ == "__main__":
    main()
