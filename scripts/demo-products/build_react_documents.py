#!/usr/bin/env python3
"""시연 핵심 상품(청자 찻잔·분청 찻잔·한지 조명·모란도 액자)의 AI 상세 페이지 JSON(react_document, schemaVersion 2.0)을 만들어 V24 로 심는다.

AI 가 돌려주는 형식(섹션 hero·features·detail·usage·spec·closing, img 노드는 imageId+alt)과 같은 구조로 만들고,
소비자 화면이 바로 그릴 수 있게 img 노드에 props.src(공개 주소)도 함께 넣는다. 합죽선(p31)은 AI 결과 원본을 쓴다(V22).
이미지는 본문용 8장(body_<키>_NN)이 들어오면 IMAGES 를 바꿔 다시 실행한다. 지금은 실사 A·B·C 를 임시로 쓴다.
실행: python3 scripts/demo-products/build_react_documents.py
"""
import json
import os
import re

HERE = os.path.dirname(__file__)
ROOT = os.path.join(HERE, "..", "..")
MANIFEST = os.path.join(ROOT, "docs", "demo-products", "photoreal", "demo-body", "manifest.json")
V17 = os.path.join(ROOT, "src", "main", "resources", "db", "migration", "V17__demo_curated_products.sql")
README = os.path.join(ROOT, "docs", "demo-products", "README.md")
OUT = os.path.join(ROOT, "src", "main", "resources", "db", "migration", "V24__demo_react_documents.sql")
RAW = "https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products"
ZERO = {"top": 0.0, "right": 0.0, "bottom": 0.0, "left": 0.0}
TAGLINE = {"p32": "비색에 학이 내려앉은 찻잔", "p33": "붓질이 그대로 남은 분청 찻잔", "p15": "빛을 머금은 한지 조명", "p11": "비단 위에 피어난 모란"}
# 섹션 이미지: 본문용 8장이 없으면 실사 A·B·C 를 쓴다
IMAGES = {k: {"hero": f"{RAW}/photoreal/staging/{k}_A.webp", "detail": f"{RAW}/photoreal/staging/{k}_B.webp",
              "usage": f"{RAW}/photoreal/staging/{k}_C.webp"} for k in TAGLINE}


def text(id_, value):
    return {"id": id_, "type": "text", "value": value, "marks": []}


def el(id_, tag, props, children):
    return {"id": id_, "type": "element", "tag": tag, "props": props, "children": children}


def font(size, weight, line):
    return {"fontFamily": "sans", "fontSize": size, "fontWeight": weight, "lineHeight": line, "margin": dict(ZERO)}


def stack(gap=16.0):
    return {"layout": {"display": "stack", "gap": gap, "wrap": False, "align": "stretch"}, "style": {"margin": dict(ZERO)}}


def leaf(id_, tag, style, value):
    return el(id_, tag, {"style": style}, [text(f"{id_}-text", value)])


def copy(prefix, eyebrow, title, body, title_size=30.0):
    return el(f"{prefix}-copy", "div", stack(), [
        leaf(f"{prefix}-eyebrow", "span", font(13.0, 500, 1.5), eyebrow),
        leaf(f"{prefix}-title", "h2", font(title_size, 700, 1.35 if title_size < 40 else 1.25), title),
        leaf(f"{prefix}-body", "p", font(16.0, 400, 1.7), body)])


def image(prefix, n, imgid, src, alt, fit):
    pos = {"x": 0.5, "y": 0.5}
    img = el(f"{prefix}-image-{n:02d}", "img", {"style": {"objectFit": fit, "objectPosition": pos, "margin": dict(ZERO)},
                                                "imageId": imgid, "alt": alt, "src": src}, [])
    return el(f"{prefix}-figure-{n:02d}", "figure", {"style": {"margin": dict(ZERO)}}, [img])


def section(idx, name, variant, color, bg, layout, children):
    pad = {"top": 40.0, "right": 40.0, "bottom": 40.0, "left": 40.0}
    return el(f"section-{idx:02d}-{name}-root", "section", {
        "variant": variant, "layout": layout,
        "style": {"color": color, "backgroundColor": bg, "padding": pad, "margin": dict(ZERO)}}, children)


STACK = {"display": "stack", "gap": 24.0, "wrap": False, "align": "stretch"}
ROW = {"display": "flex", "direction": "row", "gap": 24.0, "wrap": False, "align": "center"}


def card(prefix, n, title, body):
    pad = {"top": 20.0, "right": 20.0, "bottom": 20.0, "left": 20.0}
    return el(f"{prefix}-card-{n:02d}", "article", {
        "layout": {"display": "stack", "gap": 12.0, "wrap": False, "align": "stretch"},
        "style": {"color": "#222222", "backgroundColor": "#E6EEEF", "borderRadius": 12.0, "padding": pad, "margin": dict(ZERO)}}, [
        leaf(f"{prefix}-card-{n:02d}-title", "h3", font(19.0, 600, 1.4), title),
        leaf(f"{prefix}-card-{n:02d}-body", "p", font(16.0, 400, 1.7), body)])


def spec_row(prefix, n, label, value):
    th = {"style": font(16.0, 600, 1.5), "scope": "row"}
    return el(f"{prefix}-row-{n:02d}", "tr", {}, [
        el(f"{prefix}-row-{n:02d}-label", "th", th, [text(f"{prefix}-row-{n:02d}-label-text", label)]),
        el(f"{prefix}-row-{n:02d}-value", "td", {"style": font(16.0, 400, 1.7)}, [text(f"{prefix}-row-{n:02d}-value-text", value)])])


def document(key, name, shots, desc, material, features, item):
    img = IMAGES[key]
    hero = section(1, "hero", "dark", "#F7F4EE", "#242424", STACK, [
        copy("section-01-hero", "PRODUCT STORY", TAGLINE[key], desc, 40.0),
        el("section-01-hero-media", "div", stack(), [image("section-01-hero", 1, f"body_{key}_01", img["hero"], f"{name} 대표", "contain")])])
    cards = [card("section-02-features", i, shots[j]["heading"], shots[j]["caption"]) for i, j in enumerate((1, 2, 4), 1)]
    feats = section(2, "features", "paper", "#222222", "#F6F2EC", STACK, [
        copy("section-02-features", "VISIBLE DETAILS", "가까이 볼수록 선명해지는 디테일", "사진에서 확인되는 특징을 정리했습니다."),
        el("section-02-features-cards", "div", {"layout": {"display": "grid", "columns": 3, "gap": 16.0, "wrap": False, "align": "stretch"},
                                                "style": {"margin": dict(ZERO)}}, cards)])
    detail = section(3, "detail", "light", "#222222", "#F7F7F5", ROW, [
        copy("section-03-detail", "MATERIAL DETAIL", shots[2]["heading"], shots[2]["caption"]),
        el("section-03-detail-media", "div", stack(), [image("section-03-detail", 1, f"body_{key}_03", img["detail"], f"{name} {shots[2]['heading']}", "contain")])])
    usage = section(4, "usage", "sand", "#222222", "#EEE5D8", STACK, [
        copy("section-04-usage", "IN DAILY SPACE", shots[5]["heading"], shots[5]["caption"]),
        el("section-04-usage-media", "div", stack(), [image("section-04-usage", 1, f"body_{key}_06", img["usage"], f"{name} {shots[5]['heading']}", "cover")])])
    rows = [("제품 유형", item), ("소재", material), ("특징", " · ".join(features))]
    table = el("section-05-spec-table", "table", {"layout": {"display": "stack", "gap": 12.0, "wrap": False, "align": "stretch"}, "style": {"margin": dict(ZERO)}}, [
        leaf("section-05-spec-table-caption", "caption", font(13.0, 500, 1.5), "제품 정보"),
        el("section-05-spec-table-body", "tbody", {}, [spec_row("section-05-spec", i, a, b) for i, (a, b) in enumerate(rows, 1)])])
    spec = section(5, "spec", "paper", "#222222", "#F6F2EC", STACK, [copy("section-05-spec", "PRODUCT NOTE", "제품 정보", "이미지와 검수된 상품 정보로 확인 가능한 범위입니다."), table])
    closing = section(6, "closing", "dark", "#F7F4EE", "#242424", STACK, [
        copy("section-06-closing", "CRAFTSMANSHIP", "한 점의 차이", f"손으로 만든 {item}이라 사진과 결·색이 조금씩 다를 수 있습니다. 그 차이가 이 작품만의 멋입니다.")])
    return {"schemaVersion": "2.0", "canvasWidth": 774, "root": [hero, feats, detail, usage, spec, closing]}


def main():
    manifest = json.load(open(MANIFEST, encoding="utf-8"))
    desc = {}
    for m in re.finditer(r"^    \('((?:[^']|'')*)', (\d+), (\d+), '((?:[^']|'')*)', '((?:[^']|'')*)', (\d+),", open(V17, encoding="utf-8").read(), re.M):
        desc[m.group(1).replace("''", "'")] = (int(m.group(2)), m.group(4).replace("''", "'").split(" 특징:")[0], m.group(5))
    feats = {}
    for line in open(README, encoding="utf-8"):
        m = re.match(r"\| (p\d\d) \| ([^|]+?) \| \d+ \| \d+ \| [^|]+ \| [^|]+ \| ([^|]+?) \|", line)
        if m:
            feats[m.group(2).strip()] = [f.strip() for f in m.group(3).split("/")]
    items = {"p32": "찻잔", "p33": "찻잔", "p15": "조명", "p11": "자수 액자"}
    rows = []
    for key in TAGLINE:
        shots = [x for x in manifest if x["product"] == key]
        name = shots[0]["name"]
        artisan, d, material = desc[name]
        doc = document(key, name, shots, d, material, feats[name], items[key])
        rows.append((name, artisan, json.dumps(doc, ensure_ascii=False, separators=(",", ":"))))
    q = lambda s: "'" + s.replace("'", "''") + "'"
    values = ",\n".join(f"    ({q(n)}, {a}, {q(j)})" for n, a, j in rows)
    sql = f"""-- 시연 핵심 상품 4개의 AI 상세 페이지 JSON(react_document 2.0)을 저장한다. 원본: scripts/demo-products/build_react_documents.py
-- 전주 합죽선(p31)은 V22 에서 AI 결과 원본을 넣었다. 소비자 화면이 바로 그릴 수 있게 img 노드에 props.src 도 들어 있다.

UPDATE content c SET react_document = d.doc, updated_at = NOW()
FROM product p JOIN (VALUES
{values}
) AS d(title, artisan_id, doc) ON d.title = p.title AND d.artisan_id = p.artisan_id
WHERE c.product_id = p.product_id;
"""
    open(OUT, "w", encoding="utf-8").write(sql)
    print(len(rows), "documents ->", OUT, len(sql) // 1024, "KB")


if __name__ == "__main__":
    main()
