#!/usr/bin/env python3
"""전체 상품(큐레이션 33 + 대표 56 + 변형 180 + 소재 확대 19) 실사 이미지 프롬프트를 만든다.

결과: docs/demo-products/photoreal/all/
  - prompts_all.txt   번호·파일 키·프롬프트 (한 줄이 이미지 한 장)
  - batches.txt       5개씩 묶어 그대로 붙여 넣는 배치
  - manifest.json     번호 → 키·종류·대상 (적용 스크립트가 쓴다)
  - master_prompt_NN.txt 대화형 AI에 한 번에 붙여 넣는 60개 묶음(8개)
실행: python3 scripts/demo-products/photoreal_all.py
"""
import importlib.util
import json
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
OUT = os.path.join(ROOT, "docs", "demo-products", "photoreal", "all")
sys.path.insert(0, os.path.dirname(__file__))
import photoreal_prompts as pp  # noqa: E402

spec = importlib.util.spec_from_file_location("variants", os.path.join(os.path.dirname(__file__), "variants.py"))
V = importlib.util.module_from_spec(spec)
spec.loader.exec_module(V)

A_TAIL, B_TAIL, C_TAIL = pp.A_TAIL, pp.B_TAIL, pp.C_TAIL
DONE = {"p31", "p32", "p33", "p15"}  # 이미 받은 상품(A·B·C)

# 큐레이션 중 prompts_imagefx 에 아직 없는 13개 + 이미 있는 16개는 기존 문장을 쓴다
CURATED_NEW = {
    "p17": ("모시 주방 앞치마", "a natural ramie kitchen apron with a front pocket and neck and waist ties, in soft off-white linen tone",
            "a wooden kitchen counter beside a ceramic bowl and fresh vegetables, window light"),
    "p18": ("모시 홑이불", "a thin ivory ramie summer bed sheet, neatly folded",
            "a made bed with the ivory ramie sheet in a calm bedroom, morning light"),
    "p19": ("민트 모시 보자기", "a square mint-green ramie bojagi wrapping cloth with neat stitched edges",
            "the mint ramie bojagi wrapping a small gift box on a wooden table"),
    "p20": ("모시 개량한복 상의", "an off-white ramie modern hanbok top with simple clean lines",
            "the top hanging on a wooden hanger against a plain light wall, window light"),
    "p21": ("은 매듭 팔찌", "a sterling-silver bracelet with a hand-tied Korean knot and adjustable length",
            "the bracelet resting on a small dark ceramic dish on a wooden table"),
    "p22": ("옥 노리개 브로치", "a jade-green stone ornament brooch with hand-tied silk knot and tassel",
            "the brooch pinned on a folded cream silk cloth"),
    "p23": ("칠보 푸른 반지", "a sterling-silver ring with deep blue cloisonne enamel",
            "the ring on a small ceramic ring dish beside a dried flower"),
    "p24": ("금빛 떨잠", "a gold-plated silver ornamental hair pin (ddeoljam) with fine carving and small trembling charms",
            "the hair ornament laid on dark velvet with a soft window light"),
    "p25": ("진주 매듭 목걸이", "a freshwater-pearl necklace with a hand-tied Korean knot clasp, 45 cm",
            "the necklace laid on a linen cloth beside a small mirror"),
    "p27": ("나전 필통", "a wooden pencil case with mother-of-pearl floral inlay on dark lacquer",
            "the pencil case on a dark writing desk with pens inside and an open notebook"),
    "p28": ("칠보 문양 만년필", "a fountain pen with a blue cloisonne enamel barrel and gold trim",
            "the pen lying on a cream notebook page beside an ink bottle"),
    "p29": ("서예 붓·먹·벼루 세트", "a calligraphy set: two brushes on a rest, an ink stick and a dark stone inkstone",
            "the calligraphy set on a low wooden desk with white paper, window light"),
    "p30": ("한지 편지지 세트", "a set of handmade hanji letter paper and envelopes tied with a thin cord",
            "the hanji stationery set on a wooden desk with a pen and a small flower"),
}

SUBNOUN = {
    1: "a Korean tea cup", 2: "a Korean serving bowl and plate", 3: "a Korean spoon and chopsticks set",
    4: "a Korean liquor cup and small bottle", 5: "a small Korean traditional tray table (soban)",
    6: "a kitchen cutting board", 7: "a Korean storage jar", 8: "a Korean cooking pot with lid",
    9: "a Korean ritual vessel set", 10: "a table lamp", 11: "a jewelry box", 12: "a flower vase",
    13: "a small desk clock", 14: "a hanging window blind screen", 15: "a small piece of furniture (chest)",
    16: "a decorative furniture hardware handle and hinge", 17: "a pair of rings and earrings",
    18: "a necklace and bracelet", 19: "a Korean norigae ornament", 20: "a hairpin (binyeo)",
    21: "a scarf", 22: "a small pouch bag", 23: "a slim card wallet", 24: "a keyring", 25: "a folding fan",
    26: "a Korean everyday hanbok jacket", 27: "a Korean gat hat", 28: "a Korean headband ornament (mangeon)",
    29: "a calligraphy brush and inkstone set", 30: "a stationery letter paper set", 31: "a pen",
    32: "a business card case", 33: "a pen holder", 34: "a floor cushion", 35: "a blanket",
    36: "a table runner", 37: "a bojagi wrapping cloth", 38: "a handkerchief", 39: "an apron",
    40: "a woven floor mat", 41: "a small embroidery piece in a frame", 42: "a framed artwork",
    43: "a folding screen", 44: "a carved calligraphy plaque", 45: "a small wood carving figure",
    46: "a Korean traditional mask", 47: "a Korean ceremonial dagger", 48: "a Korean traditional drum",
    49: "a Buddhist temple bell ornament", 50: "a set of collectible traditional coins",
    51: "a bottle of Korean traditional liquor", 52: "a package of tea leaves", 53: "a jar of Korean fermented paste",
    54: "a jar of kimchi", 55: "a box of Korean rice cakes and hangwa confections", 56: "a gift set of Korean royal court food",
}
MAT = {
    "wood": "natural solid hardwood with visible grain", "lacquer": "glossy deep red-brown urushi lacquer",
    "najeon": "black lacquer inlaid with iridescent mother-of-pearl", "enamel": "deep blue cloisonne enamel with fine gold wire",
    "brass": "hand-hammered golden bangjja brass", "silver": "polished sterling silver with fine engraving",
    "jade": "translucent jade-green stone", "celadon": "pale jade-green celadon glaze with fine crackle",
    "porcelain": "white porcelain with blue cobalt painting", "onggi": "earthy brown onggi earthenware",
    "silk": "lustrous silk in soft colors", "hanji": "handmade mulberry hanji paper",
    "bamboo": "natural carved and woven bamboo", "leather": "natural leather and horsehair",
    "stone": "dark polished inkstone", "textile": "natural ramie, hemp or cotton weave",
    "paste": "red-brown Korean fermented paste", "tea": "dried green tea leaves", "rice": "glutinous rice, grain and traditional sweets",
}
MACRO = {
    "wood": "wood grain with growth rings and a fine oiled finish", "lacquer": "a glossy deep lacquer surface with a soft reflection",
    "najeon": "iridescent mother-of-pearl pieces set in black lacquer", "enamel": "cobalt-blue enamel cells divided by thin gold wire",
    "brass": "dense hand-hammering marks on golden brass", "silver": "fine engraved patterns on polished silver",
    "jade": "translucent green jade with soft internal veins", "celadon": "pale green glaze with a fine crackle network",
    "porcelain": "white glaze with brushed blue cobalt lines", "onggi": "rough earthenware surface with natural glaze streaks",
    "silk": "the lustrous weave and sheen of silk threads", "hanji": "long mulberry fibers in handmade paper",
    "bamboo": "bamboo fibers and woven strips", "leather": "leather grain and horsehair strands",
    "stone": "the fine matte grain of a dark inkstone", "textile": "the open weave of natural ramie and hemp",
    "paste": "a spoon of red fermented paste in an earthenware dish", "tea": "curled dried green tea leaves",
    "rice": "glossy rice-cake and hangwa confections close up",
}
# 대표 56 — 영어 설명
FLAG = {
    1: "a buncheong tea set: teapot and cups with grayish slip brush decoration", 2: "a set of golden brass bangjja bowls and plates for four",
    3: "a matte golden bangjja brass spoon and chopsticks set", 4: "a brass kettle and four brass liquor cups",
    5: "a small traditional Naju soban tray table in dark red lacquer", 6: "a thick whole zelkova wood cutting board",
    7: "a large 20-liter onggi soy sauce jar with glossy brown glaze", 8: "a brown onggi hot-pot ttukbaegi bowl with lid",
    9: "a large set of lacquered wooden ritual vessels", 10: "a wooden music box lamp with a lotus-flower shaped glowing acrylic top",
    11: "a wooden jewelry box with red plum mother-of-pearl inlay and hanji lining", 12: "a white porcelain vase with blue-and-white painting",
    13: "a wooden desk clock with ten-longevity-symbols mother-of-pearl inlay", 14: "a natural hemp and silk-thread hanging blind",
    15: "a four-tier open wooden shelf (sabang takja) in zelkova wood", 16: "a pair of brass butterfly-shaped furniture hardware",
    17: "a pair of sterling silver traditional rings", 18: "a necklace with chick-shaped hand-tied knot and natural jade bead",
    19: "a three-piece silk norigae ornament with long tassels", 20: "a sterling silver hairpin with dragon-head finial",
    21: "a jade-green Hansan ramie scarf with fringe", 22: "a silk patchwork tote bag with wooden handle",
    23: "a leather wallet with mother-of-pearl accent", 24: "a set of Hahoe mask wooden keyrings",
    25: "a hanji and bamboo folding fan with plum blossom painting", 26: "a custom silk hanbok set with jeogori and skirt",
    27: "a black horsehair and bamboo Joseon gat hat", 28: "a woven horsehair mangeon headband with jade button",
    29: "a set of three handmade calligraphy brushes", 30: "a handmade hanji letter paper and envelope set",
    31: "a ballpoint pen with butterfly mother-of-pearl inlay", 32: "a business card case with red plum mother-of-pearl inlay",
    33: "a pencil case with red plum mother-of-pearl and hanji", 34: "a pair of silk brocade floor cushions",
    35: "a silk blanket filled with natural cotton", 36: "a naturally dyed ramie table runner",
    37: "a small silk bojagi with embroidered court insignia", 38: "a microfiber glasses cloth handkerchief with embroidery",
    39: "a naturally dyed ramie kitchen apron", 40: "a hanji and bamboo umbrella in seven colors",
    41: "a framed peony satin-stitch embroidery in a wooden frame", 42: "a canvas framed art of the Sun, Moon and Five Peaks painting",
    43: "a four-panel gold-leaf folding screen of the Sun, Moon and Five Peaks", 44: "a carved wooden plaque with relief carving of Hwaseong Haenggung",
    45: "a pair of carved wooden mandarin ducks with flowers", 46: "a Hahoe yangban mask carved in paulownia wood",
    47: "traditional bamboo arrows with pheasant feather fletching", 48: "a traditional dragon drum with wooden body and leather head",
    49: "a brass lotus-shaped wind chime bell", 50: "five bangjja brass maepae horse-warrant tokens on a cord",
    51: "a 500 ml bottle of Korean traditional liquor in a ceramic bottle", 52: "a 30 g package of premium green tea leaves with a small tea bowl",
    53: "a 320 g jar of traditional fermented soybean paste in a brass-colored lid", 54: "a 3 kg container of white gardenia kimchi",
    55: "a three-tier holiday gift box of traditional hangwa sweets", 56: "a gift set of hangwa sweets and dried beef jerky",
}


def cap(s):
    return s[0].upper() + s[1:]


def curated_rows():
    rows = []
    for line in open(os.path.join(OUT, "..", "prompts_imagefx.txt"), encoding="utf-8"):
        m = re.match(r"\[(\d+)\] (p\d\d)_([ABC])\.png \((.*?)\) \| (.*)", line.strip())
        if m and m.group(2) not in DONE:
            rows.append((m.group(2), m.group(3), m.group(4), m.group(5)))
    for key, (name, short, scene) in CURATED_NEW.items():
        rows.append((key, "A", f"{name} · 대표", f"Product photo of {short}, {A_TAIL}"))
        rows.append((key, "B", f"{name} · 소재 확대", f"Macro close-up of the material and craftsmanship of {short}, {B_TAIL}"))
        rows.append((key, "C", f"{name} · 장면", f"{cap(scene)}. The product is {short}, {C_TAIL}"))
    rows.sort(key=lambda r: (r[0], r[1]))
    return rows


def main():
    P = json.load(open(os.path.join(os.path.dirname(__file__), "seed_products.json"), encoding="utf-8"))
    for p in P:
        p["cls"] = V.classify(p["material"])
    flag = {}
    for p in sorted(P, key=lambda x: x["id"]):
        flag.setdefault(p["sub"], p)
    fids = {p["id"] for p in flag.values()}
    combos = sorted({(p["sub"], p["cls"]) for p in P if p["id"] not in fids})
    items = []  # (tier, key, kind, label, prompt, target)
    for key, cut, label, prompt in curated_rows():
        items.append((1, f"{key}_{cut}", cut, label, prompt, {"type": "curated", "key": key}))
    for sub, p in sorted(flag.items()):
        d = FLAG[sub]
        k = f"f{sub:02d}"
        title = p["title"]
        items.append((2, f"{k}_A", "A", f"{title} · 대표", f"Product photo of {d}, {A_TAIL}", {"type": "flagship", "sub": sub}))
        items.append((2, f"{k}_B", "B", f"{title} · 소재 확대", f"Macro close-up of the material and craftsmanship of {d}, {B_TAIL}", {"type": "flagship", "sub": sub}))
        items.append((2, f"{k}_C", "C", f"{title} · 장면", f"{cap(d)} in a natural, realistic setting where it is normally used, no people. {C_TAIL}", {"type": "flagship", "sub": sub}))
    names = {s: n for s, n, _c, _k in V.seed.ITEMS}
    for sub, cls in combos:
        items.append((3, f"s{sub:02d}-{cls}_A", "A", f"{names[sub]} · {V.CLASSES[cls][0]} · 대표",
                      f"Product photo of {SUBNOUN[sub]} made of {MAT[cls]}, {A_TAIL}", {"type": "variant", "sub": sub, "cls": cls}))
    for cls in sorted({p["cls"] for p in P}):
        items.append((4, f"m-{cls}_B", "B", f"{V.CLASSES[cls][0]} · 소재 확대(공용)",
                      f"Macro close-up of {MACRO[cls]}, filling the frame, {B_TAIL}", {"type": "material", "cls": cls}))
    manifest = []
    lines = ["# 전체 상품 실사 프롬프트 — 한 줄이 이미지 한 장 (정사각형 1:1)",
             "# 구성: Tier1 큐레이션 29개 상품 A·B·C / Tier2 대표 56개 A·B·C / Tier3 소분류×소재 변형 대표 / Tier4 소재 확대 공용",
             "# 번호와 키는 파일 매칭용이다. 이미지에 글자를 넣지 말 것.", ""]
    for n, (tier, key, cut, label, prompt, target) in enumerate(items, 1):
        lines.append(f"[{n:03d}] {key} (T{tier} · {label}) | {prompt}")
        manifest.append({"no": n, "tier": tier, "key": key, "cut": cut, "label": label, "target": target, "prompt": prompt})
    open(os.path.join(OUT, "prompts_all.txt"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
    json.dump(manifest, open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    head = ("아래 {n}개 번호를 각각 서로 다른 이미지 1장씩, 번호 순서대로 만들어 줘. 정사각형 1:1, 가능한 최대 해상도. "
            "한 장은 독립된 사진 하나(콜라주·표·라벨·캡션·글자·로고·사람·손 금지). 이미지마다 '[번호] 키'만 적고 다른 말은 하지 마. "
            "상품의 색과 소재는 문장 그대로 따르고 임의로 바꾸지 마.\n\n")
    out = []
    for i in range(0, len(items), 5):
        chunk = items[i:i + 5]
        body = "\n".join(f"[{i + j + 1:03d}] {c[1]} — {c[4]}" for j, c in enumerate(chunk))
        out.append(f"=== 배치 {i // 5 + 1:02d} ([{i + 1:03d}]–[{i + len(chunk):03d}]) ===\n" + head.format(n=len(chunk)) + body + "\n")
    open(os.path.join(OUT, "batches.txt"), "w", encoding="utf-8").write("\n".join(out))

    # 대화형 AI에 한 번에 붙여 넣는 마스터 프롬프트 (60개씩)
    head2 = ("너는 전통 공예 쇼핑몰 '미담'의 상품 사진 작가야. 아래 번호 목록 {a}~{b}번 이미지를 번호 순서대로 만들어 줘.\n\n"
             "진행 방식 (반드시 지켜)\n"
             "1. 처음에는 아무것도 만들지 말고 '준비 완료'라고만 답해 줘. 내가 '계속'이라고 보내면 다음 번호부터 최대 5장씩, 번호 순서대로, 서로 다른 별도 이미지로 만들어 줘.\n"
             "2. 이미지마다 아래에 '[번호] 키'만 적어 줘. 질문·설명·제안은 하지 마.\n"
             "3. 생성 한도에 걸리면 '[번호]까지 완료, 한도 도달'이라고만 말해 줘. 내가 '계속'이라고 하면 이어서 해 줘.\n"
             "4. 내가 '다시 [번호]'라고 하면 그 번호를 한 번 더 만들어 줘.\n"
             "5. 번호마다 목록의 영어 문장을 그대로 따라. 임의로 상품·색·구도를 바꾸지 마. 번호와 키는 내가 파일을 구분하는 용도이고 이미지에 쓰지 마.\n\n"
             "이미지 규칙: 정사각형 1:1, 최대 해상도. 한 장은 독립된 사진 하나(콜라주·표·여러 컷·라벨·캡션 금지). "
             "글자·로고·워터마크·사람·손·얼룩 금지. 키가 _A 인 컷은 소품과 표면 질감이 없는 단색 따뜻한 연회색 배경, 모든 _A 컷은 같은 배경 톤. "
             "_B 는 같은 상품의 소재 매크로, _C 는 사람 없는 자연스러운 생활 장면. 한국 전통 공예품의 구조와 소재감이 정확해야 해. "
             "접는 부채는 양쪽 가장자리에 굵은 대가 하나씩 있고 그 사이에 가는 살이 있어.\n\n번호 목록\n")
    for k in range(0, len(items), 60):
        chunk = items[k:k + 60]
        body = "\n\n".join(f"[{k + j + 1:03d}] {c[1]}\n{c[4]}" for j, c in enumerate(chunk))
        tail = "\n\n위 설명을 모두 이해했으면 아무것도 만들지 말고 '준비 완료'라고만 답해 줘."
        open(os.path.join(OUT, f"master_prompt_{k // 60 + 1:02d}.txt"), "w", encoding="utf-8").write(
            head2.format(a=k + 1, b=k + len(chunk)) + body + tail + "\n")
    from collections import Counter
    print(Counter(i[0] for i in items), len(items), "batches", len(out))


if __name__ == "__main__":
    main()
