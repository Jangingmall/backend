#!/usr/bin/env python3
"""시연 핵심 5개 상품의 '본문용 이미지 8장 세트' 프롬프트를 만든다 (5상품 × 8장 = 40장).

갤러리용 8장(photoreal_seller.py)과 겹치지 않게, 제작 과정·재료·도구·진행 중인 작품·진열 같은 본문 장면을 담는다.
사람은 그리지 않는다(AI 가 손·얼굴을 자주 틀린다). 결과: docs/demo-products/photoreal/demo-body/{prompts.txt, manifest.json, master_prompt.txt}
실행: python3 scripts/demo-products/photoreal_demo_body.py
"""
import json
import os

import photoreal_prompts as pp

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "demo-products", "photoreal", "demo-body")
GUARD = "full-bleed square frame with no white bars or borders, no people, no hands, no text, no logos, no watermark"
STYLE = "photograph, soft window light, warm neutral tones, shallow depth of field, photorealistic"
CUTS = ["재료", "도구", "작업대", "진행 중인 작품", "기법 확대", "진열", "변형 비교", "선물 구성"]

PRODUCTS = [
    ("p31", "전주 합죽선 · 매화선", pp.SHORT["p31"], [
        "Raw materials for making a folding fan: split bamboo strips and sheets of white mulberry hanji paper on a wooden table",
        "Fan-making tools laid out on a workbench: a small bamboo knife, a brush, an ink stone and a thin ink stick",
        "A quiet Korean workshop bench with bamboo ribs drying on a rack beside stacks of hanji paper, nobody in the scene",
        "A half-assembled folding fan frame with bamboo ribs fanned out before the paper is attached, on a work table",
        "Close-up of fine black ink plum branch strokes and pale pink petals painted on white hanji paper",
        "Several finished folding fans of the same kind displayed standing on a wooden shelf in a bright room",
        "Two folding fans with different plum blossom paintings placed side by side on a plain light surface to show that no two are alike",
        "The folding fan with a small gift box, a linen ribbon and a handwritten-style blank card, arranged as a gift set"]),
    ("p32", "청자 운학문 찻잔", pp.SHORT["p32"], [
        "Raw materials for celadon pottery: a lump of fine grey clay, a jar of translucent glaze and a bowl of white slip on a wooden table",
        "Pottery tools on a workbench: carving needles, a sponge, a small brush and a wooden rib beside a potter's wheel",
        "A quiet pottery studio shelf with unfired cups drying on boards in rows, nobody in the scene",
        "An unglazed bisque tea cup with carved crane and cloud lines waiting for glaze, on a workbench",
        "Close-up of white and black slip inlay of cranes and clouds on pale jade-green celadon glaze with fine crackle",
        "Several finished pale jade-green celadon tea cups displayed on a wooden shelf in a bright room",
        "Two celadon tea cups of the same kind with slightly different glaze depth placed side by side on a plain light surface",
        "A celadon tea cup in a gift set with a small tea caddy, a bamboo tea scoop and a cloth wrap in an open wooden box"]),
    ("p33", "분청 귀얄 찻잔", pp.SHORT["p33"], [
        "Raw materials for buncheong pottery: grey stoneware clay, a bucket of white slip and iron-brown pigment on a wooden table",
        "Pottery tools on a workbench: a wide flat brush for slip, a trimming tool and a sponge beside a potter's wheel",
        "A quiet pottery studio with cups wet with white slip drying on boards in rows, nobody in the scene",
        "A tea cup freshly brushed with white slip before firing, on a workbench",
        "Close-up of wide brush-stroke white slip texture and iron-brown wildflower pattern on grayish stoneware",
        "Several finished buncheong tea cups displayed on a wooden shelf in a bright room",
        "Two buncheong tea cups with different brush-stroke directions placed side by side on a plain light surface",
        "A buncheong tea cup on a linen cloth with a small tea caddy and a bamboo whisk arranged as a tea set"]),
    ("p15", "한지 무드 조명", pp.SHORT["p15"], [
        "Raw materials for a hanji lamp: sheets of handmade mulberry paper, thin wooden slats and a block of solid walnut on a work table",
        "Lamp-making tools on a workbench: a small hand plane, a glue brush, a ruler and a pencil, with hanji sheets nearby",
        "A quiet woodworking bench with lamp frames without paper standing in a row, nobody in the scene",
        "A lamp frame half covered with hanji paper, on a workbench",
        "Close-up of long mulberry fibers glowing warmly in a lit hanji paper shade",
        "Several lit hanji lamps of different sizes displayed in a row on a wooden shelf in a dim room",
        "Three hanji lamps of small, medium and large size lit side by side on a plain surface",
        "A hanji lamp in its gift box with soft packing paper and a cloth bag, box slightly open"]),
    ("p11", "수자수 모란도 액자", pp.SHORT["p11"], [
        "Raw materials for silk embroidery: spools of pink, green and cream silk thread and a piece of undyed silk on a wooden table",
        "Embroidery tools on a workbench: needles in a pincushion, small scissors and a wooden embroidery hoop",
        "A quiet embroidery studio corner with a stand frame holding stretched silk, nobody in the scene",
        "A pink peony half embroidered with satin stitch in a wooden hoop, on a workbench",
        "Close-up of satin-stitch silk threads of pink peony petals with a fine highlight",
        "Several framed embroideries of the same kind displayed on a wall above a wooden console table in a bright room",
        "Two framed peony embroideries with different color tones placed side by side leaning on a plain wall",
        "The framed embroidery wrapped in protective paper with a ribbon, arranged as a gift"]),
]


def build():
    items = []
    for key, name, short, scenes in PRODUCTS:
        for i, scene in enumerate(scenes, 1):
            prompt = f"{scene}. The product is {short}, {STYLE}, {GUARD}" if i in (6, 7, 8, 5) else f"{scene}. The product made here is {short}, {STYLE}, {GUARD}"
            items.append({"key": f"body_{key}_{i:02d}", "product": key, "name": name, "cut": CUTS[i - 1], "prompt": prompt})
    return items


def main():
    os.makedirs(OUT, exist_ok=True)
    items = build()
    for n, it in enumerate(items, 1):
        it["no"] = n
    lines = ["# 시연 핵심 5개 상품 본문용 이미지 8장 세트 — 40장 (정사각형 1:1)", ""]
    lines += [f"[{it['no']:02d}] {it['key']} ({it['name']} · {it['cut']}) | {it['prompt']}" for it in items]
    open(os.path.join(OUT, "prompts.txt"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
    json.dump(items, open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    head = ("너는 전통 공예 쇼핑몰 '미담'의 상품 상세 페이지 본문 사진 작가야. 아래 번호 목록 1~40번 이미지를 번호 순서대로 만들어 줘.\n\n"
            "진행 방식 (반드시 지켜)\n"
            "0. 번호 하나당 이미지는 정확히 1장만 만들어. 내가 '다시 [번호]'라고 할 때만 다시 만들어.\n"
            "1. 처음에는 아무것도 만들지 말고 '준비 완료'라고만 답해 줘. 내가 '계속'이라고 보내면 다음 번호부터 최대 5장씩, 번호 순서대로, 서로 다른 별도 이미지로 만들어 줘.\n"
            "2. 이미지 제목(파일 이름)을 반드시 그 번호의 키(예: body_p31_01)로 정하고, 아래에 '[번호] 키'만 적어 줘. 질문·설명·제안은 하지 마.\n"
            "3. 생성 한도에 걸리면 '[번호]까지 완료, 한도 도달'이라고만 말해 줘. 내가 '계속'이라고 하면 이어서 해 줘.\n"
            "4. 같은 상품(키의 p31 등)의 8장은 같은 공방·같은 재료·같은 작품 세계의 사진이어야 해. 색·소재를 장마다 바꾸지 마.\n"
            "5. 정사각형 1:1, 최대 해상도. 한 장은 독립된 사진 하나(콜라주·표·라벨·캡션 금지). 글자·로고·워터마크·사람·손 금지.\n\n번호 목록\n")
    body = "\n\n".join(f"[{it['no']:02d}] {it['key']}\n{it['prompt']}" for it in items)
    open(os.path.join(OUT, "master_prompt.txt"), "w", encoding="utf-8").write(
        head + body + "\n\n위 설명을 모두 이해했으면 아무것도 만들지 말고 '준비 완료'라고만 답해 줘.\n")
    print(len(items), "prompts")


if __name__ == "__main__":
    main()
