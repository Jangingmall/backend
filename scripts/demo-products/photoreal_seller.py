#!/usr/bin/env python3
"""판매자 시연용 '업로드 사진 8장 세트' 프롬프트를 만든다 (상품 5개 × 8장 = 40장).

판매자 스튜디오는 사진 1~8장을 올리고 AI 가 상세 페이지를 만든다. 시연 때 판매자가 올릴 사진 묶음이 필요해서,
구매자용 A·B·C 와 별개로 한 상품을 여러 각도·장면으로 찍은 8장 세트를 만든다.
결과: docs/demo-products/photoreal/seller/{prompts.txt, manifest.json, master_prompt.txt}
실행: python3 scripts/demo-products/photoreal_seller.py
"""
import json
import os

import photoreal_prompts as pp

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "demo-products", "photoreal", "seller")
GUARD = "full-bleed square frame with no white bars or borders, no people, no hands, no text, no logos, no watermark"
PLAIN = ("centered, plain seamless warm light-grey backdrop and floor, no props, wide margin, soft light from upper left, "
         "soft shadow, photorealistic, sharp focus")
CLOSE = "macro photograph, shallow depth of field, soft light, photorealistic"
SCENE = "lifestyle photograph, soft window light, warm neutral tones, shallow depth of field, photorealistic"

# 상품별: (키, 이름, 설명, 앞뒤 구분, 디테일1, 디테일2, 사용 장면, 포장)
PRODUCTS = [
    ("p31", "전주 합죽선 · 매화선", pp.SHORT["p31"],
     ("the fan fully opened, seen from the front", "the fan half-opened seen from a 45-degree angle", "the fan closed, lying flat, showing the bamboo outer guard sticks"),
     "the ink plum blossom branch painting on the paper face", "the bamboo ribs and the small knotted tassel pendant at the handle",
     "the opened fan resting on a dark wooden tray beside a small tea cup in a quiet hanok room",
     "the closed fan in an open paulownia gift box with a linen ribbon"),
    ("p32", "청자 운학문 찻잔", pp.SHORT["p32"],
     ("the cup seen straight from the front", "the cup seen from a 45-degree angle from above showing the inside", "the underside of the cup showing the foot ring"),
     "the inlaid white cranes and clouds on the glaze", "the fine crackle pattern of the pale jade-green glaze at the rim",
     "the cup filled with green tea on a small wooden tea tray beside a linen cloth",
     "the cup nested in a cream cloth wrapping inside an open wooden box"),
    ("p33", "분청 귀얄 찻잔", pp.SHORT["p33"],
     ("the cup seen straight from the front", "the cup seen from a 45-degree angle from above showing the inside", "the underside of the cup showing the foot ring and the unglazed clay"),
     "the brush-stroke white slip texture on the grayish surface", "the iron-brown wildflower pattern and the glaze edge",
     "the cup on a linen cloth beside a small tea caddy in natural window light",
     "the cup wrapped in kraft paper inside an open cardboard gift box"),
    ("p15", "한지 무드 조명", pp.SHORT["p15"],
     ("the lamp switched on, seen from the front", "the lamp switched on, seen from a 45-degree angle", "the lamp switched off in daylight, seen from the front"),
     "the hanji paper shade fibers glowing with warm light", "the solid wood base joint and the lamp cord",
     "the lit lamp on a bedside table in a dim cozy bedroom",
     "the lamp's neat cardboard gift box and wrapping, box slightly open"),
    ("p11", "수자수 모란도 액자", pp.SHORT["p11"],
     ("the framed embroidery seen straight from the front", "the framed embroidery seen from a 30-degree angle showing the frame depth", "the back of the frame showing the wooden backing"),
     "the satin-stitch silk threads of the pink peony petals", "the wooden frame corner and the mounting edge of the fabric",
     "the framed embroidery hanging on a wall above a small wooden console table",
     "the framed embroidery packed in protective paper in an open shipping box"),
]
CUTS = ["정면", "비스듬히", "뒷면·바닥", "디테일1", "디테일2", "사용 장면", "포장", "전체 맥락"]


def build():
    items = []
    for key, name, short, views, d1, d2, scene, pack in PRODUCTS:
        prompts = [
            f"Product photo of {short}, {views[0]}, {PLAIN}, {GUARD}",
            f"Product photo of {short}, {views[1]}, {PLAIN}, {GUARD}",
            f"Product photo of {short}, {views[2]}, {PLAIN}, {GUARD}",
            f"Close-up of {d1} of {short}, {CLOSE}, {GUARD}",
            f"Close-up of {d2} of {short}, {CLOSE}, {GUARD}",
            f"{scene[0].upper() + scene[1:]}. The product is {short}, {SCENE}, {GUARD}",
            f"Product packaging photo: {pack}. The product is {short}, {SCENE}, {GUARD}",
            f"Wide shot of a calm interior styled around {short}, the product clearly visible but not centered, {SCENE}, {GUARD}",
        ]
        for i, prompt in enumerate(prompts, 1):
            items.append({"key": f"seller_{key}_{i:02d}", "product": key, "name": name, "cut": CUTS[i - 1], "prompt": prompt})
    return items


def main():
    os.makedirs(OUT, exist_ok=True)
    items = build()
    for n, it in enumerate(items, 1):
        it["no"] = n
    lines = ["# 판매자 업로드용 사진 8장 세트 — 상품 5개 × 8장 = 40장 (정사각형 1:1)", ""]
    lines += [f"[{it['no']:02d}] {it['key']} ({it['name']} · {it['cut']}) | {it['prompt']}" for it in items]
    open(os.path.join(OUT, "prompts.txt"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
    json.dump(items, open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    head = ("너는 전통 공예 쇼핑몰 '미담' 판매자의 상품 사진 작가야. 아래 번호 목록 1~40번 이미지를 번호 순서대로 만들어 줘.\n\n"
            "진행 방식 (반드시 지켜)\n"
            "0. 번호 하나당 이미지는 정확히 1장만 만들어. 마음에 안 들어도 스스로 다시 만들지 마. 내가 '다시 [번호]'라고 할 때만 다시 만들어.\n"
            "1. 처음에는 아무것도 만들지 말고 '준비 완료'라고만 답해 줘. 내가 '계속'이라고 보내면 다음 번호부터 최대 5장씩, 번호 순서대로, 서로 다른 별도 이미지로 만들어 줘.\n"
            "2. 이미지마다 이미지 제목(파일 이름)을 반드시 그 번호의 키(예: seller_p31_01)로 정하고, 아래에 '[번호] 키'만 적어 줘. 질문·설명·제안은 하지 마.\n"
            "3. 생성 한도에 걸리면 '[번호]까지 완료, 한도 도달'이라고만 말해 줘. 내가 '계속'이라고 하면 이어서 해 줘.\n"
            "4. 같은 상품(같은 앞 4글자 키 p31 등)의 8장은 같은 물건의 사진이어야 해. 색·소재·모양을 장마다 바꾸지 마.\n"
            "5. 정사각형 1:1, 최대 해상도. 한 장은 독립된 사진 하나(콜라주·표·라벨·캡션 금지). 글자·로고·워터마크·사람·손 금지.\n\n번호 목록\n")
    body = "\n\n".join(f"[{it['no']:02d}] {it['key']}\n{it['prompt']}" for it in items)
    open(os.path.join(OUT, "master_prompt.txt"), "w", encoding="utf-8").write(
        head + body + "\n\n위 설명을 모두 이해했으면 아무것도 만들지 말고 '준비 완료'라고만 답해 줘.\n")
    print(len(items), "prompts")


if __name__ == "__main__":
    main()
