#!/usr/bin/env python3
"""상세 소개가 없는 시드 상품 전부에 AI 상세 페이지 JSON(react_document 2.0)과 소비자용 content_block 을 만들어 V26 으로 심는다.

상품 사진(대표·상세)과 설명을 읽어 4개 섹션(hero · 이미지로 읽는 특징 · 쓰임과 관리 · 제품 정보)을 구성한다.
입력은 마이그레이션이 모두 적용된 로컬 DB(DEMO_DB, 기본 demo)에서 읽는다. 상품은 (제목, 소분류 ID)로 찾는다.
실행: python3 scripts/demo-products/build_all_react_documents.py
"""
import importlib.util
import json
import os
import re
import subprocess

HERE = os.path.dirname(__file__)
ROOT = os.path.join(HERE, "..", "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "db", "migration", "V26__demo_all_react_documents.sql")
RAW = "https://raw.githubusercontent.com/Jangingmall/backend/develop/"
TOKEN = "@R@"


def load(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


BD = load("build_react_documents")
RP = BD.RP
SURFACE = {
    "brass": "두드린 자국과 금속 특유의 묵직한 광택", "celadon": "맑은 유약층과 은은한 비색", "porcelain": "매끈한 유약과 깨끗한 흰 바탕",
    "onggi": "흙의 거친 결과 잿물이 만든 자연스러운 윤기", "lacquer": "겹겹이 입힌 옻칠의 깊은 윤기", "najeon": "자개가 빛을 따라 번지는 영롱한 무늬",
    "enamel": "매끈하게 입힌 색 유약과 선명한 문양", "silver": "은의 부드러운 빛과 손으로 다듬은 선", "jade": "맑게 비치는 옥의 결과 부드러운 곡선",
    "silk": "촘촘한 실의 결과 은은한 광택", "hanji": "섬유가 겹쳐 만든 한지의 부드러운 결", "bamboo": "곧게 뻗은 대나무의 마디와 가지런한 결",
    "leather": "손으로 다듬은 가죽의 결과 바느질 선", "stone": "돌의 단단한 질감과 다듬은 면", "wood": "나무의 결과 손으로 깎아 낸 모서리",
    "textile": "올이 고른 천의 짜임과 부드러운 질감", "paste": "정성껏 담아 낸 재료의 색과 윤기", "tea": "잎의 모양과 맑은 색", "rice": "곡식의 고른 알갱이와 깨끗한 색"}


def fetch():
    sql = """SELECT json_agg(t) FROM (
      SELECT p.title, p.subcategory_id AS sub, p.material, p.description, p.thumbnail_url AS thumb, p.price,
             (SELECT json_agg(d.image_url ORDER BY d.display_order) FROM product_detail_image d WHERE d.product_id = p.product_id) AS details,
             s.name AS subname
      FROM product p JOIN subcategory s ON s.subcategory_id = p.subcategory_id
      WHERE NOT EXISTS (SELECT 1 FROM content c WHERE c.product_id = p.product_id AND c.react_document IS NOT NULL)
      ORDER BY p.product_id) t"""
    out = subprocess.run(["su", "postgres", "-c", f"psql -d {os.environ.get('DEMO_DB', 'demo')} -tA -c \"{sql}\""],
                         capture_output=True, text=True, check=True).stdout
    return json.loads(out)


def slim(node):
    """기본값과 같은 값(0 여백·sans 글꼴)은 빼서 JSON 크기를 줄인다."""
    if isinstance(node, dict):
        return {k: slim(v) for k, v in node.items()
                if not (k == "margin" and all(x == 0.0 for x in v.values())) and not (k == "fontFamily" and v == "sans")
                and not (k == "style" and v == {}) and not (k == "props" and v == {})}
    if isinstance(node, list):
        return [slim(x) for x in node]
    return node


def rel(url):
    return url.replace(RAW, TOKEN) if url else None


def sentences(desc):
    return [s.strip() for s in re.findall(r"[^.]+\.", desc or "") if s.strip()]


def values(p):
    """상품마다 달라지는 문장·이미지. 문서 틀(템플릿)의 @토큰@ 자리에 들어간다."""
    sub = p["sub"]
    cls = RP.V.classify(p["material"] or "")
    item = RP.ITEM.get(sub, "공예품")
    sent = sentences(p["description"]) or [f"손으로 만든 {item}입니다."]
    imgs = [rel(p["thumb"])] + [rel(u) for u in (p["details"] or [])]
    return {"TITLE": p["title"], "MAKE": sent[0], "USE": sent[1] if len(sent) > 1 else RP.USE[sub][0],
            "CARE": sent[2] if len(sent) > 2 else RP.CARE[cls][0], "ITEM": item, "MAT": p["material"] or "천연 소재",
            "SUB": p["subname"], "SURF": SURFACE.get(cls, "손으로 다듬은 결"), "IMGS": imgs}


def template(n):
    """사진이 n장(1~3)인 상품의 문서 틀. 텍스트는 @토큰@, 이미지는 @U1@~@U3@."""
    def pic(prefix, k, fit):
        return BD.image(prefix, k, f"img_{k:02d}", f"@U{k}@", f"@TITLE@ {k}번째 사진", fit)
    hero = BD.section(1, "hero", "dark", "#F7F4EE", "#242424", BD.STACK, [
        BD.copy("section-01-hero", "HANDMADE · @ITEM@", "@TITLE@", "@MAKE@", 36.0),
        el_div("section-01-hero-media", [pic("section-01-hero", 1, "contain")])])
    cards = [("사진으로 보이는 결", "@SURF@이 사진 속에서 먼저 눈에 들어옵니다. 소재는 @MAT@입니다."), ("만든 방식", "@MAKE@"), ("쓰임새", "@USE@")]
    feat = [BD.copy("section-02-features", "IMAGE READING", "사진으로 읽는 특징", "@TITLE@의 사진에서 확인되는 질감과 만든 방식, 쓰임을 정리했습니다."),
            el_div("section-02-features-cards", [BD.card("section-02-features", i, a, b) for i, (a, b) in enumerate(cards, 1)], grid=True)]
    if n > 1:
        fig = pic("section-02-features", 2, "cover")
        fig["children"].append(BD.el("section-02-features-caption", "figcaption", {"style": BD.font(14.0, 500, 1.5)},
                                     [BD.text("section-02-features-caption-text", "가까이서 본 @ITEM@의 결과 마감")]))
        feat.append(fig)
    usage = [BD.copy("section-03-usage", "USE AND CARE", "쓰는 법과 관리", "@USE@ @CARE@")]
    if n > 2:
        usage.append(pic("section-03-usage", 3, "cover"))
    rows = [("제품 유형", "@ITEM@"), ("소재", "@MAT@"), ("분류", "@SUB@"), ("제작", "장인이 한 점씩 손으로 만듭니다")]
    table = BD.el("section-04-spec-table", "table", {"layout": dict(BD.STACK, gap=12.0), "style": {"margin": dict(BD.ZERO)}}, [
        BD.leaf("section-04-spec-table-caption", "caption", BD.font(13.0, 500, 1.5), "제품 정보"),
        BD.el("section-04-spec-table-body", "tbody", {}, [BD.spec_row("section-04-spec", i, a, b) for i, (a, b) in enumerate(rows, 1)])])
    root = [hero, BD.section(2, "features", "paper", "#222222", "#F6F2EC", BD.STACK, feat),
            BD.section(3, "usage", "sand", "#222222", "#EEE5D8", BD.STACK, usage),
            BD.section(4, "spec", "light", "#222222", "#F7F7F5", BD.STACK, [
                BD.copy("section-04-spec", "PRODUCT NOTE", "제품 정보", "손으로 만든 @ITEM@이라 사진과 결·색이 조금씩 다를 수 있습니다. 그 차이가 이 작품만의 멋입니다."), table])]
    return json.dumps(slim({"schemaVersion": "2.0", "canvasWidth": 774, "root": root}), ensure_ascii=False, separators=(",", ":"))


def el_div(id_, children, grid=False):
    layout = {"display": "grid", "columns": 3, "gap": 16.0, "wrap": False, "align": "stretch"} if grid else dict(BD.STACK, gap=16.0)
    return BD.el(id_, "div", {"layout": layout, "style": {"margin": dict(BD.ZERO)}}, children)


def main():
    q = lambda s: "'" + s.replace("'", "''") + "'"
    rows = []
    for p in fetch():
        v = values(p)
        u = (v["IMGS"] + [None, None])[:3]
        rows.append("    (" + ", ".join([q(p["title"]), str(p["sub"]), str(min(len(v["IMGS"]), 3))] + [q(v[k]) for k in ("MAKE", "USE", "CARE", "ITEM", "MAT", "SUB", "SURF")]
                                         + [q(x) if x else "NULL" for x in u]) + ")")
    tpl = {n: template(n) for n in (1, 2, 3)}
    rows_sql = ",\n".join(rows)
    js = "pg_temp.demo_js"
    repl = "".join(f"{js}(r.{c})" for c in ())
    chain = "t.doc"
    for tok, col in (("TITLE", "title"), ("MAKE", "make"), ("USE", "use_s"), ("CARE", "care"), ("ITEM", "item"), ("MAT", "mat"), ("SUB", "sub_n"), ("SURF", "surf"),
                     ("U1", "u1"), ("U2", "u2"), ("U3", "u3")):
        chain = f"REPLACE({chain}, '@{tok}@', {js}(COALESCE(r.{col}, '')))"
    tpl_sql = ",\n".join(f"    ({n}, {q(t)})" for n, t in tpl.items())
    sql = f"""-- 상세 소개가 없던 시드 상품 {len(rows)}개에 AI 상세 페이지 JSON(react_document 2.0)과 소비자용 본문 블록을 심는다. 원본: scripts/demo-products/build_all_react_documents.py
-- 문서 틀 3종(사진 1·2·3장)의 @토큰@ 자리에 상품별 문장·사진을 넣는다. @R@ 는 저장소 raw 주소({RAW})로 바꾼다.
-- 이미 react_document 가 있는 상품과 본문 블록이 있는 상품은 건드리지 않는다.

CREATE FUNCTION pg_temp.demo_js(t TEXT) RETURNS TEXT LANGUAGE sql IMMUTABLE AS
$$ SELECT substr(to_jsonb(t)::text, 2, length(to_jsonb(t)::text) - 2) $$;

CREATE TEMP TABLE demo_all_tpl (n INT, doc TEXT) ON COMMIT DROP;
INSERT INTO demo_all_tpl VALUES
{tpl_sql};

CREATE TEMP TABLE demo_all_rows (title VARCHAR(200), subcategory_id BIGINT, n INT, make TEXT, use_s TEXT, care TEXT, item TEXT, mat TEXT,
    sub_n TEXT, surf TEXT, u1 TEXT, u2 TEXT, u3 TEXT) ON COMMIT DROP;
INSERT INTO demo_all_rows VALUES
{rows_sql};

CREATE TEMP TABLE demo_all_docs ON COMMIT DROP AS
SELECT p.product_id, REPLACE({chain.replace('r.title', 'p.title')}, '@R@', '{RAW}') AS doc
FROM demo_all_rows r JOIN product p ON p.title = r.title AND p.subcategory_id = r.subcategory_id
JOIN demo_all_tpl t ON t.n = r.n;

INSERT INTO content (product_id, status, version, fact_check_confirmed, photo_match_confirmed, display_approval_badge, react_document, created_at, updated_at)
SELECT d.product_id, 'PUBLISHED', 0, TRUE, TRUE, TRUE, d.doc, NOW(), NOW()
FROM demo_all_docs d
WHERE NOT EXISTS (SELECT 1 FROM content c WHERE c.product_id = d.product_id);

-- 큐레이션 상품처럼 본문 블록만 있고 JSON 이 없던 상품에는 JSON 만 채운다
UPDATE content c SET react_document = d.doc, updated_at = NOW()
FROM demo_all_docs d
WHERE c.product_id = d.product_id AND c.react_document IS NULL;

-- 소비자 상세는 content_block 을 순서대로 보여 준다
INSERT INTO content_block (content_id, display_order, tag, text, image_url)
SELECT c.content_id, ROW_NUMBER() OVER (PARTITION BY c.content_id ORDER BY b.ord), b.tag, b.body, REPLACE(b.img, '@R@', '{RAW}')
FROM demo_all_rows r JOIN product p ON p.title = r.title AND p.subcategory_id = r.subcategory_id
JOIN content c ON c.product_id = p.product_id
CROSS JOIN LATERAL (VALUES
    (1, 'img', NULL, r.u1), (2, 'h2', r.title, NULL), (3, 'p', r.make, NULL),
    (4, 'h2', '사진으로 읽는 특징', NULL), (5, 'p', r.surf || '이 사진 속에서 먼저 눈에 들어옵니다. 소재는 ' || r.mat || '입니다.', NULL),
    (6, 'img', NULL, CASE WHEN r.n > 1 THEN r.u2 END),
    (7, 'h2', '쓰임새', NULL), (8, 'p', r.use_s, NULL),
    (9, 'img', NULL, CASE WHEN r.n > 2 THEN r.u3 END),
    (10, 'h2', '쓰는 법과 관리', NULL), (11, 'p', r.care, NULL),
    (12, 'p', '손으로 만든 ' || r.item || '이라 사진과 결·색이 조금씩 다를 수 있습니다.', NULL)) AS b(ord, tag, body, img)
WHERE (b.tag <> 'img' OR b.img IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM content_block x WHERE x.content_id = c.content_id);
"""
    open(OUT, "w", encoding="utf-8").write(sql)
    print(len(rows), "products ->", OUT, len(sql) // 1024, "KB")


if __name__ == "__main__":
    main()
