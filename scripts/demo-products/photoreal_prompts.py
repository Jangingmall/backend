#!/usr/bin/env python3
"""실사 이미지 프롬프트 파일을 만든다 (docs/demo-products/photoreal/prompts_imagefx.txt, master_prompt.txt).

원본 문장은 docs/demo-products/photoreal-brief.md 의 상품별 프롬프트(A·B·C)다. 이 스크립트는
- A(대표): 단색 스튜디오 배경, 소품·표면 질감 없음(홈 카드 톤 통일)
- B(소재 확대)·C(장면): 같은 상품으로 보이도록 색·소재를 담은 짧은 상품 설명을 앞에 붙인다(그림 도구가 앞 컷을 기억하지 못하므로)
를 적용해 두 파일을 다시 만든다.
실행: python3 scripts/demo-products/photoreal_prompts.py
"""
import os
import re

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
BRIEF = os.path.join(ROOT, "docs", "demo-products", "photoreal-brief.md")
OUT = os.path.join(ROOT, "docs", "demo-products", "photoreal")

# 색·소재가 컷마다 달라지지 않도록 붙이는 짧은 상품 설명
SHORT = {
    "p31": "a white mulberry-paper (hanji) folding fan with ink and pale-pink plum blossom branches and natural bamboo ribs",
    "p32": "a pale jade-green celadon tea cup with white-and-black inlaid crane-and-cloud pattern",
    "p33": "a grayish-white buncheong tea cup with brushed white slip and iron-brown wildflower pattern",
    "p06": "a crimson-red silk hand-knotted Korean norigae ornament with long tassels",
    "p11": "a framed pink peony silk satin-stitch embroidery in a plain natural wood frame",
    "p15": "a warm amber-glowing hanji paper lamp on a solid wood base",
    "p16": "a sky-blue thin ramie scarf with small fringe",
    "p26": "a black lacquer card case with turquoise-green mother-of-pearl floral inlay",
    "p01": "a pale pink ramie hanbok jacket (jeogori)",
    "p02": "an indigo-blue quilted cotton lucky pouch with a knotted cord",
    "p03": "two aubergine-purple cotton handkerchiefs with embroidered plum blossoms",
    "p04": "a five-color (blue, yellow, red, white, black) silk patchwork zipper pouch",
    "p05": "a light yellow-green ramie scarf with fringe",
    "p07": "an indigo-blue hand-knotted keyring with a tassel",
    "p08": "a slim teal and blue-green silk patchwork card wallet",
    "p09": "a slender polished silver hairpin with a carved plum blossom head",
    "p10": "jade-green bead earrings with small hand-tied knots and silver posts",
    "p12": "a framed ink-and-light-green hanji landscape painting in a natural wood frame",
    "p13": "a square ochre-brown quilted silk cushion with diamond stitching",
    "p14": "an ivory ramie table runner with fringed ends",
}
FIX_P31A = "two thick outer guard sticks on both the left and right edges of the fan, no stains or smudges"
A_TAIL = ("centered, plain seamless warm light-grey backdrop and floor, no props, no table texture, wide margin, "
          "soft light from upper left, soft shadow, photorealistic, sharp focus, no text")
B_TAIL = "macro photograph, shallow depth of field, soft light, warm light-grey tone, photorealistic, no text"
C_TAIL = "lifestyle photograph, no people, shallow depth of field, soft window light, warm neutral tones, photorealistic, no text"
LAB = dict(A="대표", B="소재 확대", C="장면")


def cap(text):
    return text[0].upper() + text[1:]


def items():
    text = open(BRIEF, encoding="utf-8").read().split("\n## 6. ")[0]
    parts = re.split(r"\n### (p\d\d) · ([^\n]+)\n", text)
    out = []
    for i in range(1, len(parts), 3):
        key, name, body = parts[i], parts[i + 1], parts[i + 2]
        blocks = re.findall(r"```text\n(.*?)\n```", body, re.S)
        a = re.match(r"Create image A of (.*?)\. Professional", blocks[0], re.S).group(1)
        b = re.match(r"Create image B: (.*?)\. Same product", blocks[1], re.S).group(1)
        c = re.match(r"Create image C: (.*?)\. Same product", blocks[2], re.S).group(1)
        short = SHORT[key]
        a_prompt = f"Product photo of {a}, {A_TAIL}" + (f", {FIX_P31A}" if key == "p31" else "")
        out.append((key, name, "A", a_prompt))
        out.append((key, name, "B", f"{cap(b)} of {short}, {B_TAIL}"))
        out.append((key, name, "C", f"{cap(c)}. The product is {short}, {C_TAIL}"))
    assert len(out) == 60
    return out


def main():
    rows = items()
    imagefx = ["# ImageFX(labs.google/fx) 용 프롬프트 60개 — 한 줄이 한 번의 생성이다",
               "# 설정: 가로세로 비율 '정사각형(1:1)'. 한 번에 여러 장이 나오면 가장 좋은 1장만 내려받는다.",
               "# 저장 이름: <key>_<A|B|C>.png. 대화형이 아니라 한 번에 생성하는 도구라 설명·질문 문장 없이 장면 묘사만 쓴다.",
               "# B·C 는 그림 도구가 앞 컷을 기억하지 못하므로 상품의 색·소재 설명을 포함한다.", ""]
    for n, (key, name, cut, prompt) in enumerate(rows, 1):
        imagefx.append(f"[{n:02d}] {key}_{cut}.png ({name} · {LAB[cut]}) | {prompt}")
    open(os.path.join(OUT, "prompts_imagefx.txt"), "w", encoding="utf-8").write("\n".join(imagefx) + "\n")

    head = """너는 전통 공예 쇼핑몰 '미담'의 상품 사진 작가야. 아래 60개 번호 목록의 이미지를 번호 순서대로 만들어 줘.

진행 방식 (반드시 지켜)
1. 내가 '계속'이라고 보내면 다음 번호부터 이미지를 만들어 줘(한 번에 최대 5장까지, 번호 순서대로, 서로 다른 별도 이미지). 처음에는 [01]부터 시작해.
2. 이미지마다 아래에 '[번호] 파일명'만 적어 줘. 질문·설명·제안은 하지 마.
3. 이미지 생성 한도에 걸리면 '[번호]까지 완료, 한도 도달'이라고만 말해 줘. 한도가 풀려 내가 '계속'이라고 하면 이어서 해 줘.
4. 내가 '다시 [번호]'라고 하면 그 번호를 한 번 더 만들어 줘.
5. 번호마다 아래 목록의 영어 문장을 그대로 따라. 임의로 상품·색·구도를 바꾸지 마.

이미지 규칙 (모든 번호 공통)
- 한 장은 독립된 사진 하나. 콜라주·표·여러 컷 나열·라벨·캡션 금지.
- 정사각형 1:1, 가능한 최대 해상도(1024px 이상).
- A(대표) 컷: 소품·표면 질감 없는 단색 따뜻한 밝은 회색 배경과 바닥. 모든 A 컷은 같은 배경 톤.
- B(소재 확대): 같은 상품의 소재·무늬 매크로. C(장면): 사람 없는 자연스러운 생활 장면. B·C 는 문장에 적힌 상품(색·소재)과 같은 상품이어야 해.
- 글자, 로고, 워터마크, 사람, 손은 넣지 마. 얼룩·번짐 같은 이물질도 없게.
- 한국 전통 공예품의 구조와 소재감이 정확해야 해. 접는 부채는 양쪽 가장자리에 굵은 대가 하나씩 있고 그 사이에 가는 살이 있는 구조야.

번호 목록 (파일명은 내가 저장할 때 쓰는 이름이야. 이미지에 쓰지 마)
"""
    body = [f"[{n:02d}] {key}_{cut}.png — {name} · {LAB[cut]}\n{prompt}" for n, (key, name, cut, prompt) in enumerate(rows, 1)]
    tail = "\n\n위 설명을 모두 이해했으면 아무것도 만들지 말고 '준비 완료'라고만 답해 줘. 내가 '계속'이라고 하면 [01]부터 시작해."
    open(os.path.join(OUT, "master_prompt.txt"), "w", encoding="utf-8").write(head + "\n" + "\n\n".join(body) + tail + "\n")
    print("prompts_imagefx.txt, master_prompt.txt 생성", max(len(r[3]) for r in rows))


if __name__ == "__main__":
    main()
