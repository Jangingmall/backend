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
import importlib.util  # noqa: E402

_spec = importlib.util.spec_from_file_location("rename_products", os.path.join(HERE, "rename_products.py"))
RP = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(RP)
SUB = {"p32": 1, "p33": 1, "p15": 10, "p11": 41}
# 섹션 이미지: 본문용 8장이 없으면 실사 A·B·C 를 쓴다
BODY_DIR = os.path.join(ROOT, "docs", "demo-products", "photoreal", "staging")
FALLBACK = {1: "A", 2: "B", 3: "C", 4: "A", 5: "B", 6: "C", 7: "A", 8: "B"}


def slot_src(key, n):
    """본문용 이미지(body_<키>_NN.webp)가 저장소에 있으면 그것을, 없으면 실사 A·B·C 를 임시로 쓴다."""
    own = f"body_{key}_{n:02d}.webp"
    if os.path.exists(os.path.join(BODY_DIR, own)):
        return f"{RAW}/photoreal/staging/{own}"
    return f"{RAW}/photoreal/staging/{key}_{FALLBACK[n]}.webp"


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


def document(key, name, shots, desc, material, features, item, sub):
    src = lambda n: slot_src(key, n)
    hero = section(1, "hero", "dark", "#F7F4EE", "#242424", STACK, [
        copy("section-01-hero", "PRODUCT STORY", TAGLINE[key], desc, 40.0),
        el("section-01-hero-media", "div", stack(), [image("section-01-hero", 1, f"body_{key}_01", src(1), f"{name} {shots[0]['heading']}", "contain")])])
    tech = RP.TECH[RP.V.classify(material)][int(key[1:]) % 3].format(item=RP.ITEM[sub])
    care, use = RP.CARE[RP.V.classify(material)], RP.USE[sub]
    pairs = list(zip((features + ["손으로 만든 한 점", "소재의 결", "쓰는 즐거움"])[:3], (tech, care[0], use[0])))
    cards = [card("section-02-features", i, a, b) for i, (a, b) in enumerate(pairs, 1)]
    feats = section(2, "features", "paper", "#222222", "#F6F2EC", STACK, [
        copy("section-02-features", "VISIBLE DETAILS", "가까이 볼수록 선명해지는 디테일", "만든 방식과 쓰임, 관리법을 한눈에 정리했습니다."),
        el("section-02-features-cards", "div", {"layout": {"display": "grid", "columns": 3, "gap": 16.0, "wrap": False, "align": "stretch"},
                                                "style": {"margin": dict(ZERO)}}, cards)])
    detail = section(3, "detail", "light", "#222222", "#F7F7F5", ROW, [
        copy("section-03-detail", "MATERIAL DETAIL", shots[1]["heading"], shots[1]["caption"]),
        el("section-03-detail-media", "div", stack(), [image("section-03-detail", 1, f"body_{key}_02", src(2), f"{name} {shots[1]['heading']}", "contain")])])
    detail2 = section(4, "texture", "dark", "#F7F4EE", "#242424", ROW, [
        el("section-04-texture-media", "div", stack(), [image("section-04-texture", 1, f"body_{key}_03", src(3), f"{name} {shots[2]['heading']}", "contain")]),
        copy("section-04-texture", "SURFACE AND MATERIAL", shots[2]["heading"], shots[2]["caption"])])
    usage = section(5, "usage", "sand", "#222222", "#EEE5D8", STACK, [
        copy("section-05-usage", "IN DAILY SPACE", shots[5]["heading"], shots[5]["caption"]),
        el("section-05-usage-media", "div", stack(), [image("section-05-usage", 1, f"body_{key}_06", src(6), f"{name} {shots[5]['heading']}", "cover")])])
    gal = []
    for n, j in enumerate((3, 4, 6, 7), 1):  # 사진 04·05·07·08
        fig = image("section-06-gallery", n, f"body_{key}_{j + 1:02d}", src(j + 1), f"{name} {shots[j]['heading']}", "cover")
        fig["children"].append(el(f"section-06-gallery-caption-{n:02d}", "figcaption", {"style": font(14.0, 500, 1.5)}, [
            text(f"section-06-gallery-caption-{n:02d}-text", f"{shots[j]['heading']} — {shots[j]['caption']}")]))
        gal.append(fig)
    gallery = section(6, "gallery", "light", "#222222", "#F7F7F5", STACK, [
        copy("section-06-gallery", "DETAIL VIEW", "각도별로 살펴보기", "같은 작품을 여러 각도에서 살펴봅니다."),
        el("section-06-gallery-grid", "div", {"layout": {"display": "grid", "columns": 2, "gap": 16.0, "wrap": False, "align": "stretch"},
                                              "style": {"margin": dict(ZERO)}}, gal)])
    rows = [("제품 유형", item), ("소재", material), ("특징", " · ".join(features))]
    table = el("section-07-spec-table", "table", {"layout": {"display": "stack", "gap": 12.0, "wrap": False, "align": "stretch"}, "style": {"margin": dict(ZERO)}}, [
        leaf("section-07-spec-table-caption", "caption", font(13.0, 500, 1.5), "제품 정보"),
        el("section-07-spec-table-body", "tbody", {}, [spec_row("section-07-spec", i, a, b) for i, (a, b) in enumerate(rows, 1)])])
    spec = section(7, "spec", "paper", "#222222", "#F6F2EC", STACK, [copy("section-07-spec", "PRODUCT NOTE", "제품 정보", "이미지와 검수된 상품 정보로 확인 가능한 범위입니다."), table])
    closing = section(8, "closing", "dark", "#F7F4EE", "#242424", STACK, [
        copy("section-08-closing", "CRAFTSMANSHIP", "한 점의 차이", f"손으로 만든 {item}이라 사진과 결·색이 조금씩 다를 수 있습니다. 그 차이가 이 작품만의 멋입니다.")])
    return {"schemaVersion": "2.0", "canvasWidth": 774, "root": [hero, feats, detail, detail2, usage, gallery, spec, closing]}


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
        doc = document(key, name, shots, d, material, feats[name], items[key], SUB[key])
        rows.append((name, artisan, json.dumps(doc, ensure_ascii=False, separators=(",", ":"))))
        out_dir = os.path.join(ROOT, "docs", "demo-products", "detail", "demo5")
        os.makedirs(out_dir, exist_ok=True)
        json.dump(doc, open(os.path.join(out_dir, f"{key}.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)
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
