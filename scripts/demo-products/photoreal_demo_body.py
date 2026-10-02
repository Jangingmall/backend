#!/usr/bin/env python3
"""시연 핵심 5개 상품의 '본문용 이미지 8장 세트'(기준 사진 1장에서 각도·특징별로 확장) 프롬프트와 설명을 만든다.

AI 상세 페이지는 대표 사진 한 장에서 각도·특징별 사진을 만들고, 사진마다 맞는 소제목·설명을 붙인다. 같은 방식으로,
- 기준 사진(photoreal/staging/<키>_A.webp)을 채팅에 첨부하고 같은 물건으로 8장을 만들게 하며,
- 사진마다 소제목·설명(manifest.json 의 heading·caption)을 짝지어 둔다. 이 설명은 V24 에서 본문 블록으로 들어간다.
결과: docs/demo-products/photoreal/demo-body/{manifest.json, prompts.txt, master_<키>.txt}
실행: python3 scripts/demo-products/photoreal_demo_body.py
"""
import glob
import json
import os

import photoreal_prompts as pp

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "demo-products", "photoreal", "demo-body")
GUARD = "no people, no hands, no text, no logos, no watermark, full-bleed square frame with no white bars or borders"
KEEP = "Keep exactly the same product as the attached reference photo (same shape, color, material and pattern)."

# (소제목, 설명, 영어 장면)  — 사진에 실제로 보이는 것만 설명한다
SHOTS = {
    "p31": ("전주 합죽선 · 매화선", pp.SHORT["p31"], [
        ("한눈에 보는 합죽선", "흰 한지 선면 위에 먹과 분홍빛 매화가 이어지고, 아래로 대나무 살이 모입니다. 접부채 전체의 균형이 한눈에 들어옵니다.", "the folding fan fully opened, seen straight from the front on a plain light backdrop"),
        ("선면 위의 매화", "검은 먹으로 그린 가지와 분홍 꽃송이가 한지 결 위에 번져 있습니다. 같은 그림이 하나도 없는 이유입니다.", "a close-up of the ink plum branch and pale pink blossoms on the paper face"),
        ("한지의 결", "닥섬유가 살아 있는 한지 선면의 결과 접힌 주름입니다. 빛을 받으면 은은한 결이 드러납니다.", "a macro of the folded hanji paper pleats and fibers in soft light"),
        ("비스듬한 각도", "반쯤 펼친 부채를 비스듬히 본 모습입니다. 선면의 주름과 대나무 살의 입체감이 보입니다.", "the fan half-opened seen from a 45-degree angle"),
        ("대나무 뼈대", "부채 중심에서 바깥으로 퍼지는 대나무 살과 겉대의 결입니다. 하나씩 깎아 맞춘 뼈대입니다.", "a close-up of the bamboo ribs and outer guard sticks at the fan's pivot"),
        ("손에 들면 그림이 되는 부채", "탁자 위에 펼쳐 둔 모습입니다. 선면의 매화가 공간의 그림처럼 놓입니다.", "the opened fan resting on a wooden table beside a linen cloth in warm window light"),
        ("흰 한지와 먹, 분홍 매화", "선면은 흰 한지, 검은 먹, 분홍 매화 세 색으로 정리됩니다. 대나무의 자연색이 이 색을 받칩니다.", "a flat lay of the fan on a plain surface emphasizing its white, black and pink color palette"),
        ("접어 두면", "접어 두면 얇은 대나무 겉대 두 쪽이 선면을 감싸는 단정한 막대가 됩니다.", "the fan fully closed lying flat to show the two outer bamboo guard sticks"),
    ]),
    "p32": ("청자 운학문 찻잔", pp.SHORT["p32"], [
        ("한눈에 보는 청자 찻잔", "연한 비색 유약 위에 학과 구름이 둘러진 찻잔입니다. 둥근 입술과 가벼운 몸체가 단정합니다.", "the cup seen straight from the front on a plain light backdrop"),
        ("운학문 상감", "백토와 흑토로 메운 학과 구름 문양입니다. 선 하나하나가 손으로 새겨졌습니다.", "a close-up of the inlaid white and black cranes and clouds on the glaze"),
        ("유약의 결", "유약 속 가는 균열이 그물처럼 번져 있습니다. 쓸수록 균열이 깊어집니다.", "a macro of the crackle pattern in the pale jade-green glaze"),
        ("비스듬한 각도", "찻잔을 위에서 비스듬히 본 모습입니다. 안쪽의 맑은 유약과 입술의 선이 보입니다.", "the cup seen from a 45-degree angle above showing the inside"),
        ("굽", "찻잔을 받치는 굽과 유약이 닿지 않은 흙 빛입니다. 가마에서 구워진 흔적입니다.", "the underside and foot ring of the cup"),
        ("차 한 잔과 함께", "따뜻한 차를 따라 나무 쟁반에 올린 모습입니다. 비색이 차의 빛과 어우러집니다.", "the cup filled with green tea on a small wooden tray with a linen cloth"),
        ("비색", "연한 옥빛 비색 유약이 빛에 따라 짙고 옅게 변합니다.", "a flat lay of the cup on a plain surface emphasizing the pale jade-green color"),
        ("손에 쥐면", "손안에 들어오는 크기의 찻잔을 탁자 위에 놓은 모습입니다. 가볍고 손에 감깁니다.", "the cup placed on a wooden table in soft window light with a small tea caddy beside it"),
    ]),
    "p33": ("분청 귀얄 찻잔", pp.SHORT["p33"], [
        ("한눈에 보는 분청 찻잔", "회백색 바탕에 귀얄로 쓸어 바른 흰 분장이 남은 찻잔입니다. 투박하고 따뜻한 형태입니다.", "the cup seen straight from the front on a plain light backdrop"),
        ("귀얄 분장", "넓은 붓으로 쓸어 바른 흰 흙 자국이 결을 그립니다. 같은 붓질은 두 번 없습니다.", "a close-up of the wide brush-stroke white slip texture"),
        ("철화 풀꽃", "철 안료로 그린 갈색 풀꽃 무늬가 흰 분장 위에 놓여 있습니다.", "a macro of the iron-brown wildflower pattern on the slip"),
        ("비스듬한 각도", "찻잔을 위에서 비스듬히 본 모습입니다. 안쪽 유약과 입술의 선이 보입니다.", "the cup seen from a 45-degree angle above showing the inside"),
        ("굽과 흙", "유약을 닦아 낸 굽에 드러난 거친 흙 빛입니다. 분청 특유의 질감입니다.", "the underside and unglazed foot ring of the cup showing raw clay"),
        ("다탁 위에서", "리넨 위에 놓고 작은 다기와 함께 둔 모습입니다. 소박한 다탁에 어울립니다.", "the cup on a linen cloth next to a small tea caddy in natural window light"),
        ("회백색과 갈색", "회백색 바탕, 흰 분장, 갈색 무늬 세 가지가 어울립니다.", "a flat lay of the cup on a plain surface emphasizing its gray, white and brown tones"),
        ("손에 쥐면", "손안에 들어오는 크기로 투박하게 쥐기 좋은 찻잔입니다.", "the cup placed on a wooden table in soft window light"),
    ]),
    "p15": ("한지 무드 조명", pp.SHORT["p15"], [
        ("한눈에 보는 한지 조명", "한지 갓을 두른 원목 받침의 스탠드입니다. 불을 켜면 따뜻한 빛이 갓 전체로 퍼집니다.", "the lamp switched on, seen straight from the front on a plain backdrop"),
        ("한지 결 위의 빛", "손으로 뜬 한지의 섬유가 빛을 받아 은은한 결로 드러납니다.", "a close-up of long mulberry fibers glowing in the lit hanji shade"),
        ("원목 받침", "단단한 원목으로 깎은 받침과 코드가 맞닿는 부분입니다.", "a close-up of the solid wood base and the lamp cord"),
        ("비스듬한 각도", "조명을 비스듬히 본 모습입니다. 갓의 둥근 선과 받침의 균형이 보입니다.", "the lamp switched on seen from a 45-degree angle"),
        ("꺼 두면", "불을 끄면 한지가 낮에는 부드러운 흰빛으로 보입니다.", "the lamp switched off in daylight seen from the front"),
        ("침실의 밤", "침대 옆 협탁에 놓아 어두운 방을 따뜻하게 밝히는 모습입니다.", "the lit lamp on a bedside table in a dim cozy bedroom"),
        ("호박빛", "불을 켰을 때의 호박빛이 벽과 바닥에 번집니다.", "the lit lamp on a plain surface casting warm amber light on the wall"),
        ("거실 한켠", "거실 선반 위에 두어 공간의 분위기를 바꾸는 모습입니다.", "the lamp on a wooden shelf in a calm living room"),
    ]),
    "p11": ("수자수 모란도 액자", pp.SHORT["p11"], [
        ("한눈에 보는 모란도", "분홍 모란이 수놓인 비단을 원목 액자에 표구한 작품입니다. 한 송이가 화면 가득 피어 있습니다.", "the framed embroidery seen straight from the front on a plain wall"),
        ("수자수의 결", "한 올씩 나란히 채운 수자수 실이 꽃잎의 곡선을 따라 흐릅니다.", "a close-up of the satin-stitch silk threads of the pink peony petals"),
        ("꽃술과 잎", "모란의 꽃술과 초록 잎을 실의 방향으로 표현한 부분입니다.", "a macro of the peony center and green leaves in embroidery"),
        ("비스듬한 각도", "액자를 비스듬히 본 모습입니다. 원목 틀의 두께와 비단 표면의 윤기가 보입니다.", "the framed embroidery seen from a 30-degree angle showing the frame depth"),
        ("원목 액자", "수작업으로 짠 원목 액자의 모서리와 천을 감싼 가장자리입니다.", "a close-up of the wooden frame corner and the fabric edge"),
        ("거실 벽에서", "작은 협탁 위 벽에 걸어 둔 모습입니다. 공간에 은은한 색을 더합니다.", "the framed embroidery hanging on a wall above a small wooden console table"),
        ("분홍과 초록", "분홍 꽃과 초록 잎, 바탕의 미색이 조화를 이룹니다.", "a flat view of the framed embroidery emphasizing the pink, green and cream colors"),
        ("선물로", "보호지로 싸고 리본을 묶은 선물 포장입니다.", "the framed embroidery wrapped in protective paper with a ribbon as a gift"),
    ]),
}


def build():
    items = []
    for key, (name, short, shots) in SHOTS.items():
        for i, (heading, caption, scene) in enumerate(shots, 1):
            prompt = f"{KEEP} Product photo: {scene}. The product is {short}. Soft natural light, warm neutral tones, photorealistic, sharp focus, {GUARD}"
            items.append({"key": f"body_{key}_{i:02d}", "product": key, "name": name, "no": i, "heading": heading, "caption": caption,
                          "prompt": prompt, "reference": f"docs/demo-products/photoreal/staging/{key}_A.webp"})
    return items


def main():
    os.makedirs(OUT, exist_ok=True)
    for old in glob.glob(os.path.join(OUT, "*")):
        os.remove(old)
    items = build()
    json.dump(items, open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    lines = ["# 시연 핵심 5개 상품 본문용 8장 세트 — 상품마다 기준 사진(A)을 첨부하고 만든다", ""]
    lines += [f"[{it['key']}] {it['heading']} | {it['prompt']}" for it in items]
    open(os.path.join(OUT, "prompts.txt"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
    for key, (name, short, _shots) in SHOTS.items():
        mine = [it for it in items if it["product"] == key]
        head = (f"첨부한 사진은 미담 쇼핑몰의 '{name}' 대표 사진이야. 이 사진을 기준 상품으로 삼아, 같은 물건을 각도와 특징만 바꿔 아래 8장을 만들어 줘.\n\n"
                "규칙\n"
                "1. 첨부 사진과 같은 상품이어야 해. 모양·색·소재·무늬를 바꾸지 마.\n"
                "2. 번호 하나당 이미지는 정확히 1장. 처음에는 아무것도 만들지 말고 '준비 완료'라고만 답해 줘. 내가 '계속'이라고 보내면 다음 번호부터 최대 4장씩 만들어 줘.\n"
                "3. 이미지 제목(파일 이름)은 그 번호의 키(예: body_p31_01)로 정하고, 아래에 '[키]'만 적어 줘. 설명·질문은 하지 마.\n"
                "4. 정사각형 1:1, 최대 해상도, 한 장은 독립된 사진 하나. 글자·로고·워터마크·사람·손 금지.\n"
                "5. 내가 '다시 [키]'라고 할 때만 그 번호를 다시 만들어 줘.\n\n번호 목록\n")
        body = "\n\n".join(f"[{it['key']}] ({it['heading']})\n{it['prompt']}" for it in mine)
        open(os.path.join(OUT, f"master_{key}.txt"), "w", encoding="utf-8").write(
            head + body + "\n\n위 설명을 이해했으면 아무것도 만들지 말고 '준비 완료'라고만 답해 줘.\n")
    print(len(items), "prompts")


if __name__ == "__main__":
    main()
