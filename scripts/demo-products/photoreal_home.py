#!/usr/bin/env python3
"""홈 베스트 5·기획전 4 상품(기본 조회 상위)의 고화질 AI 이미지 프롬프트를 만든다. 상품마다 3장(A 대표 · B 가까이 · C 쓰이는 모습).

지금 이 9개는 비슷한 다른 상품 사진을 임시로 쓰고 있어(예: 왕골 원형 부채 = 합죽선 사진) 상품과 사진이 어긋난다.
채팅 AI 에서 이미지를 만들어 저장소 docs/demo-products/photoreal/staging/<키>.webp 로 넣으면(1024², WebP),
seed_design_products.py 가 A 는 대표 이미지로, B·C 는 상세 이미지로 V23 에 반영한다. 기획전 배너는 banner_ex1·banner_ex2(가로형).
결과: docs/demo-products/photoreal/home/{manifest.json, prompts.txt, master_prompt.txt}
실행: python3 scripts/demo-products/photoreal_home.py
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "demo-products", "photoreal", "home")
GUARD = "photorealistic high-resolution product photography, sharp focus, soft natural light, warm neutral tones, no people, no hands, no text, no logos, no watermark, full-bleed square frame with no white bars or borders"

# 키, 상품명, 대표(A) 장면, 가까이(B) 장면, 쓰이는 모습(C) 장면
ITEMS = [
    ("home_best1", "청자 분청 찻잔", "a small handmade Korean pale-celadon tea cup with a faint jade-green glaze and subtle crackle, seen straight from the front on a plain light backdrop",
     "a close-up of the translucent pale jade glaze and fine crackle on the cup wall", "the celadon cup filled with warm green tea on a small wooden tray beside a linen cloth"),
    ("home_best2", "옻칠 원형 쟁반", "a round handmade Korean wooden tray finished with deep red-brown lacquer, seen from a slightly raised front angle on a plain light backdrop",
     "a close-up of the glossy layered lacquer surface and the rounded rim of the tray", "the lacquer tray holding two small tea cups and a plate of rice cakes on a wooden table"),
    ("home_best3", "옥 매듭 반지", "a handmade Korean ring carved from pale green jade in a knot shape, placed on a plain light stone surface",
     "a macro of the polished translucent jade and the carved knot detail of the ring", "the jade ring resting on a folded silk cloth next to a small wooden jewelry box"),
    ("home_best4", "전통 한지 무드등", "a cylindrical Korean traditional hanji paper table lamp with wooden slats, switched on glowing in warm amber, seen from the front",
     "a close-up of the long mulberry fibers glowing in the lit hanji paper and the wooden slats", "the lit hanji lamp on a bedside table in a dim, cozy bedroom"),
    ("home_best5", "왕골 원형 부채", "a round hand-woven Korean wangol (sedge) fan in warm apricot and ochre tones with a short wooden handle, seen straight from the front",
     "a macro of the fine hand-woven wangol sedge pattern and the edge binding of the round fan", "the round wangol fan resting on a wooden porch floor beside a glass of cold barley tea"),
    ("home_plan1", "대나무 조명", "a handmade Korean bamboo woven pendant lamp, switched on glowing warm, seen from the front on a plain dark backdrop",
     "a close-up of the thin woven bamboo strips and the shadows they cast when lit", "the bamboo lamp hanging above a wooden dining table in a calm room"),
    ("home_plan2", "백잔", "a small handmade Korean white porcelain cup with a clear glaze, seen straight from the front on a plain light backdrop",
     "a close-up of the smooth translucent white glaze and the thin rim of the cup", "the white cup filled with pale tea on a wooden tray beside a small teapot"),
    ("home_plan3", "자연염 테이블 러너", "a long handmade Korean ramie table runner naturally dyed in gray-brown, soft pink and green, laid across a wooden table seen from above",
     "a macro of the loosely woven ramie texture and the natural dye gradation of the runner", "the table runner under a simple Korean meal setting with ceramic bowls"),
    ("home_plan4", "산수화 대형 부채", "a large opened Korean folding fan of hanji paper painted with an ink and light-color landscape, seen straight from the front",
     "a close-up of the ink-wash mountains and the paper texture on the fan face", "the opened landscape fan standing on a wooden stand beside a window like a framed painting"),
]
BANNERS = [
    ("banner_ex1", "장인이 빚은 공간의 온기", "a wide cinematic banner of a calm living room corner with a bamboo woven lamp glowing, a white porcelain cup and a natural-dyed ramie runner on a wooden table, soft warm light, wide 3:1 landscape frame"),
    ("banner_ex2", "바람을 부르는 부채 모음", "a wide cinematic banner of several Korean folding and round fans (hanji, lacquered black, ramie, sedge) arranged on a wooden floor in soft summer light, wide 3:1 landscape frame"),
]


def main():
    os.makedirs(OUT, exist_ok=True)
    manifest, lines, n = [], [], 0
    for key, name, a, b, c in ITEMS:
        for suffix, scene in (("A", a), ("B", b), ("C", c)):
            n += 1
            prompt = f"{scene}. {GUARD}"
            manifest.append({"no": n, "key": f"{key}_{suffix}", "product": name, "prompt": prompt})
            lines.append(f"{n:02d}. [{key}_{suffix}] {name}\n{prompt}\n")
    for key, name, scene in BANNERS:
        n += 1
        prompt = f"{scene}. photorealistic high-resolution photography, no people, no text, no logos, no watermark"
        manifest.append({"no": n, "key": key, "product": name, "prompt": prompt})
        lines.append(f"{n:02d}. [{key}] {name}\n{prompt}\n")
    json.dump(manifest, open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    open(os.path.join(OUT, "prompts.txt"), "w", encoding="utf-8").write("\n".join(lines))
    master = ["아래 요청 번호마다 정확히 1장씩, 1024×1024 정사각형 사진(배너 2장만 3:1 가로형)을 만들어 주세요.",
              "각 요청의 제목은 [키]입니다. 저장할 때 파일 이름을 [키].jpg 로 하고, 번호를 건너뛰거나 합치지 마세요.",
              "사진 속에 사람·손·글자·로고·워터마크가 없어야 합니다.", ""] + lines
    open(os.path.join(OUT, "master_prompt.txt"), "w", encoding="utf-8").write("\n".join(master))
    print(n, "prompts ->", OUT)


if __name__ == "__main__":
    main()
