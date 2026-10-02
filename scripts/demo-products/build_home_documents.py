#!/usr/bin/env python3
"""홈 베스트 5·기획전 4 상품의 AI 상세 페이지 JSON(react_document 2.0)을 AI 가 돌려주는 모양(섹션 hero·features·detail·usage·gallery·spec·closing)으로 만든다.

사진은 상품마다 3장(A 대표 · B 가까이 · C 쓰이는 모습)이고, 사진마다 imageId(home_<그룹><번호>_<A|B|C>)와 소제목·설명을 짝지었다.
결과: V30__demo_home_react_documents.sql (JSON 과 소비자용 본문 블록을 교체) + docs/demo-products/detail/home9/<키>.json
실행: python3 scripts/demo-products/build_home_documents.py
"""
import importlib.util
import json
import os
import re

HERE = os.path.dirname(__file__)
ROOT = os.path.join(HERE, "..", "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "db", "migration", "V30__demo_home_react_documents.sql")
JSON_DIR = os.path.join(ROOT, "docs", "demo-products", "detail", "home9")
RAW = "https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/photoreal/staging"


def load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


BD = load("build_react_documents")
SD = load("seed_design_products")
ALL = load("build_all_react_documents")
RP = BD.RP
ZERO = BD.ZERO


def sentences(text):
    return [s.strip() for s in re.findall(r"[^.]+\.", text) if s.strip()]


def build(group, no, title, sub, material, desc):
    key = f"home_{group}{no}"
    item = RP.ITEM[sub]
    cls = RP.V.classify(material)
    sent = sentences(desc)
    tech = RP.TECH[cls][no % 3].format(item=item)
    care, use = RP.CARE[cls], RP.USE[sub]
    surface = ALL.SURFACE.get(cls, "손으로 다듬은 결")
    shots = [
        (f"한눈에 보는 {title}", sent[0]),
        ("가까이서 본 결과 마감", f"{surface}이 가까이서 더 또렷하게 보입니다. 소재는 {material}입니다."),
        ("일상 속에서", use[0])]
    src = {n: f"{RAW}/{key}_{'ABC'[n - 1]}.webp" for n in (1, 2, 3)}

    def pic(prefix, n, fit, idx=None):
        return BD.image(prefix, n, f"{key}_{'ABC'[(idx or n) - 1]}", src[idx or n], f"{title} {shots[(idx or n) - 1][0]}", fit)

    hero = BD.section(1, "hero", "dark", "#F7F4EE", "#242424", BD.STACK, [
        BD.copy("section-01-hero", f"HANDMADE · {item}", title, sent[0], 40.0),
        BD.el("section-01-hero-media", "div", BD.stack(), [pic("section-01-hero", 1, "contain")])])
    pairs = [("만든 방식", tech), ("쓰임새", sent[1] if len(sent) > 1 else use[0]), ("오래 쓰는 법", care[0])]
    feats = BD.section(2, "features", "paper", "#222222", "#F6F2EC", BD.STACK, [
        BD.copy("section-02-features", "VISIBLE DETAILS", "가까이 볼수록 선명해지는 디테일", "만든 방식과 쓰임, 관리법을 한눈에 정리했습니다."),
        BD.el("section-02-features-cards", "div", {"layout": {"display": "grid", "columns": 3, "gap": 16.0, "wrap": False, "align": "stretch"},
                                                   "style": {"margin": dict(ZERO)}}, [BD.card("section-02-features", i, a, b) for i, (a, b) in enumerate(pairs, 1)])])
    detail = BD.section(3, "detail", "light", "#222222", "#F7F7F5", BD.ROW, [
        BD.copy("section-03-detail", "MATERIAL DETAIL", shots[1][0], shots[1][1]),
        BD.el("section-03-detail-media", "div", BD.stack(), [pic("section-03-detail", 1, "contain", 2)])])
    usage = BD.section(4, "usage", "sand", "#222222", "#EEE5D8", BD.STACK, [
        BD.copy("section-04-usage", "IN DAILY SPACE", shots[2][0], shots[2][1]),
        BD.el("section-04-usage-media", "div", BD.stack(), [pic("section-04-usage", 1, "cover", 3)])])
    figs = []
    for n in (1, 2, 3):
        fig = pic("section-05-gallery", n, "cover")
        fig["children"].append(BD.el(f"section-05-gallery-caption-{n:02d}", "figcaption", {"style": BD.font(14.0, 500, 1.5)},
                                     [BD.text(f"section-05-gallery-caption-{n:02d}-text", f"{shots[n - 1][0]} — {shots[n - 1][1]}")]))
        figs.append(fig)
    gallery = BD.section(5, "gallery", "light", "#222222", "#F7F7F5", BD.STACK, [
        BD.copy("section-05-gallery", "DETAIL VIEW", "각도별로 살펴보기", "같은 작품을 세 가지 장면으로 살펴봅니다."),
        BD.el("section-05-gallery-grid", "div", {"layout": {"display": "grid", "columns": 3, "gap": 16.0, "wrap": False, "align": "stretch"},
                                                 "style": {"margin": dict(ZERO)}}, figs)])
    rows = [("제품 유형", item), ("소재", material), ("특징", " · ".join(p[0] for p in pairs))]
    table = BD.el("section-06-spec-table", "table", {"layout": dict(BD.STACK, gap=12.0), "style": {"margin": dict(ZERO)}}, [
        BD.leaf("section-06-spec-table-caption", "caption", BD.font(13.0, 500, 1.5), "제품 정보"),
        BD.el("section-06-spec-table-body", "tbody", {}, [BD.spec_row("section-06-spec", i, a, b) for i, (a, b) in enumerate(rows, 1)])])
    spec = BD.section(6, "spec", "paper", "#222222", "#F6F2EC", BD.STACK, [BD.copy("section-06-spec", "PRODUCT NOTE", "제품 정보", "사진과 검수된 상품 정보로 확인 가능한 범위입니다."), table])
    closing = BD.section(7, "closing", "dark", "#F7F4EE", "#242424", BD.STACK, [
        BD.copy("section-07-closing", "CRAFTSMANSHIP", "한 점의 차이", f"손으로 만든 {item}이라 사진과 결·색이 조금씩 다를 수 있습니다. 그 차이가 이 작품만의 멋입니다.")])
    doc = {"schemaVersion": "2.0", "canvasWidth": 774, "root": [hero, feats, detail, usage, gallery, spec, closing]}
    blocks = [("img", None, src[1]), ("h2", title, None), ("p", sent[0], None), ("h2", "가까이 볼수록 선명해지는 디테일", None)]
    blocks += [("p", f"{a}: {b}", None) for a, b in pairs]
    blocks += [("img", None, src[2]), ("h2", shots[1][0], None), ("p", shots[1][1], None),
               ("img", None, src[3]), ("h2", shots[2][0], None), ("p", shots[2][1], None),
               ("p", f"손으로 만든 {item}이라 사진과 결·색이 조금씩 다를 수 있습니다.", None)]
    return key, doc, blocks


def main():
    q = lambda s: "'" + s.replace("'", "''") + "'"
    os.makedirs(JSON_DIR, exist_ok=True)
    docs, blocks = [], []
    for group, no, title, artisan, sub, price, material, image, colors, desc in SD.ITEMS:
        if group not in ("best", "plan"):
            continue
        key, doc, blk = build(group, no, title, sub, material, desc)
        json.dump(doc, open(os.path.join(JSON_DIR, f"{key}.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)
        docs.append(f"    ({q(title)}, {artisan}, {q(json.dumps(ALL.slim(doc), ensure_ascii=False, separators=(',', ':')))})")
        for i, (tag, body, img) in enumerate(blk, 1):
            blocks.append(f"    ({q(title)}, {artisan}, {i}, '{tag}', {q(body) if body else 'NULL'}, {q(img) if img else 'NULL'})")
    docs_sql, blocks_sql = ",\n".join(docs), ",\n".join(blocks)
    sql = f"""-- 홈 베스트 5·기획전 4 상품의 AI 상세 페이지 JSON(react_document 2.0)과 소비자용 본문 블록을 교체한다. 원본: scripts/demo-products/build_home_documents.py
-- 사진 3장(A 대표·B 가까이·C 쓰이는 모습)을 섹션 7개(hero·features·detail·usage·gallery·spec·closing)로 구성한다.

CREATE TEMP TABLE demo_home_docs (title VARCHAR(200), artisan_id BIGINT, doc TEXT) ON COMMIT DROP;
INSERT INTO demo_home_docs VALUES
{docs_sql};

CREATE TEMP TABLE demo_home_blocks (title VARCHAR(200), artisan_id BIGINT, display_order SMALLINT, tag VARCHAR(10),
    body VARCHAR(2000), image_url VARCHAR(500)) ON COMMIT DROP;
INSERT INTO demo_home_blocks VALUES
{blocks_sql};

UPDATE content c SET react_document = d.doc, updated_at = NOW()
FROM demo_home_docs d JOIN product p ON p.title = d.title AND p.artisan_id = d.artisan_id
WHERE c.product_id = p.product_id;

DELETE FROM content_block b USING content c, product p, demo_home_docs d
WHERE b.content_id = c.content_id AND c.product_id = p.product_id AND p.title = d.title AND p.artisan_id = d.artisan_id;

INSERT INTO content_block (content_id, display_order, tag, text, image_url)
SELECT c.content_id, b.display_order, b.tag, b.body, b.image_url
FROM demo_home_blocks b JOIN product p ON p.title = b.title AND p.artisan_id = b.artisan_id
JOIN content c ON c.product_id = p.product_id;
"""
    open(OUT, "w", encoding="utf-8").write(sql)
    print(len(docs), "documents,", len(blocks), "blocks ->", OUT)


if __name__ == "__main__":
    main()
