#!/usr/bin/env python3
"""전주 합죽선(p31)의 상품 상세 소개(AI 상세 페이지 결과)를 content·content_block 으로 심는 V22 마이그레이션을 만든다.

입력: docs/demo-products/detail/p31/{page_plan.json, react_document.json, *.webp}  (AI 가 만든 상세 페이지 결과)
출력: V22__demo_detail_content_p31.sql
소비자 상세는 content_block(h2·p·img) 을 순서대로 보여 준다. react_document 도 함께 저장해 편집·미리보기에서 같은 내용이 열린다.
실행: python3 scripts/demo-products/seed_detail_content.py
"""
import json
import os

HERE = os.path.dirname(__file__)
ROOT = os.path.join(HERE, "..", "..")
SRC = os.path.join(ROOT, "docs", "demo-products", "detail", "p31")
OUT = os.path.join(ROOT, "src", "main", "resources", "db", "migration", "V22__demo_detail_content_p31.sql")
RAW = "https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products/detail/p31"
TITLE, ARTISAN = "전주 합죽선 · 매화선", 41
ALT = {  # react 문서 img 노드 → 사진
    "section-01-hero-image-01": "01-hero", "section-03-detail_split-image-01": "03-detail", "section-04-palette-image-01": "06-detail-02",
    "section-05-usage_scene-image-01": "04-lifestyle", "section-06-gallery-image-01": "05-lifestyle-02", "section-06-gallery-image-02": "07-detail-03",
}
# 섹션별로 보여 줄 사진(섹션 앞에 놓는다)
PHOTOS = {"hero": ["01-hero"], "statement": ["02-packshot"], "detail_split": ["03-detail"], "palette": ["06-detail-02"], "usage_scene": ["04-lifestyle"],
          "gallery": ["05-lifestyle-02", "07-detail-03", "08-detail-04", "09-detail-05"]}


def q(s):
    return "'" + s.replace("'", "''") + "'"


def blocks():
    plan = json.load(open(os.path.join(SRC, "page_plan.json"), encoding="utf-8"))
    out = []
    for s in plan:
        kind = s["block_type"]
        for photo in PHOTOS.get(kind, []):
            out.append(("img", None, f"{RAW}/{photo}.webp"))
        out.append(("h2", s["title"], None))
        text = s["body"]
        for item in s.get("items") or []:
            text += f"\n· {item['label']} — {item['value']}. {item['description']}"
        out.append(("p", text[:2000], None))
    return out


def react_document():
    doc = json.load(open(os.path.join(SRC, "react_document.json"), encoding="utf-8"))

    def walk(node):
        if isinstance(node, dict):
            if node.get("tag") == "img" and node.get("id") in ALT:
                node.setdefault("props", {})["src"] = f"{RAW}/{ALT[node['id']]}.webp"
            for child in node.get("children") or []:
                walk(child)
    for root in doc["root"]:
        walk(root)
    return json.dumps(doc, ensure_ascii=False, separators=(",", ":"))


def main():
    rows = blocks()
    values = ",\n".join(f"    ({n}, {q(tag)}, {q(text) if text else 'NULL'}, {q(url) if url else 'NULL'})" for n, (tag, text, url) in enumerate(rows, 1))
    text = f"""-- 전주 합죽선(p31) 상품 상세 소개를 AI 상세 페이지 결과로 심는다. 원본: scripts/demo-products/seed_detail_content.py
-- content_block 은 소비자 상세가 순서대로 보여 주고, react_document 는 판매자 미리보기·편집에서 같은 내용을 연다.

CREATE TEMP TABLE demo_detail_blocks (display_order SMALLINT, tag VARCHAR(10), body VARCHAR(2000), image_url VARCHAR(500)) ON COMMIT DROP;
INSERT INTO demo_detail_blocks VALUES
{values};

INSERT INTO content (product_id, status, version, fact_check_confirmed, photo_match_confirmed, display_approval_badge, react_document, created_at, updated_at)
SELECT p.product_id, 'PUBLISHED', 0, TRUE, TRUE, TRUE, {q(react_document())}, NOW(), NOW()
FROM product p WHERE p.title = {q(TITLE)} AND p.artisan_id = {ARTISAN}
ON CONFLICT (product_id) DO UPDATE SET status = 'PUBLISHED', fact_check_confirmed = TRUE, photo_match_confirmed = TRUE,
    display_approval_badge = TRUE, react_document = EXCLUDED.react_document, updated_at = NOW();

DELETE FROM content_block b USING content c JOIN product p ON p.product_id = c.product_id
WHERE b.content_id = c.content_id AND p.title = {q(TITLE)} AND p.artisan_id = {ARTISAN};

INSERT INTO content_block (content_id, display_order, tag, text, image_url)
SELECT c.content_id, d.display_order, d.tag, d.body, d.image_url
FROM content c JOIN product p ON p.product_id = c.product_id CROSS JOIN demo_detail_blocks d
WHERE p.title = {q(TITLE)} AND p.artisan_id = {ARTISAN};
"""
    open(OUT, "w", encoding="utf-8").write(text)
    print(len(rows), "blocks ->", OUT, len(text) // 1024, "KB")


if __name__ == "__main__":
    main()
