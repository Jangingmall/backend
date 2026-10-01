#!/usr/bin/env python3
"""시연용 상품 일러스트 생성기 (실제 사진이 아니라 직접 그린 그림).

출처가 불분명한 외부 사진 대신, 공개된 사실(국립·지자체 자료, 장인 인터뷰)을 바탕으로 도형으로 그린다.
사람·실제 사진·상표가 없어 부적절한 이미지가 섞일 수 없다.

[Flow 1 전주 합죽선 · 매화선 — 반영한 사실]
- 합죽선은 대나무 겉대 두 쪽을 붙여 만든 접부채다. 부챗살은 얇게 깎은 대나무 두 쪽을 붙인 것이고 보통 38개다.
- 양 가장자리 굵은 살(변죽)은 대나무 7쪽을 붙여 만들고 매화 등을 새긴다. 부챗살에는 박쥐·운학 등을 새긴다.
- 대나무 쪽은 민어 부레를 곤 풀(민어풀)로 붙이고, 선면은 닥나무로 만든 한지(선자지)를 바른다.
- 마지막에 사북(축)을 박고 선추(고리·장식)를 단다. 전주는 조선시대 부채를 만들던 선자청이 있던 고장이다.

[Flow 2 청자·분청 찻잔 — 반영한 사실]
- 청자는 푸른빛 비색 유약, 고려 상감 기법(무늬를 파고 백토·자토를 메움)과 운학문이 대표적이다.
- 분청사기는 청자에서 백자로 넘어가는 과도기 양식으로, 그릇에 백토를 바르고 장식한다.
  기법은 상감·인화(도장)·박지·조화·철화·귀얄·담금(덤벙) 7가지다.

실행: python3 scripts/demo-images/draw_demo_products.py   (필요: pip install pillow, 한글 글꼴)
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

W = 1200          # 최종 크기
K = 2             # 안티앨리어싱용 배율
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "demo-images")
FONTS = ["/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc", "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
         "/System/Library/Fonts/AppleSDGothicNeo.ttc"]

# 상품 설명은 따로 텍스트로 있으므로 이미지에는 글자를 넣지 않는다(사람이 그림을 보고 설명과 맞는지 해석한다).
TEXT = False

PAPER, PAPER_SHADE = "#f5ecd7", "#e8dbb9"
BAMBOO, BAMBOO_DARK, BAMBOO_LIGHT = "#c9a15b", "#8d6a30", "#e6c98c"
INK, PLUM_PINK, PLUM_DEEP, BRANCH = "#2e2a26", "#f6d4dc", "#d98aa0", "#5a4330"


def font(size):
    for path in FONTS:
        if os.path.exists(path):
            return ImageFont.truetype(path, size * K)
    sys.exit("한글 글꼴이 필요합니다 (wqy-zenhei 등).")


def rgb(c):
    c = c.lstrip("#")
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    a, b = rgb(a), rgb(b)
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Canvas:
    def __init__(self, top="#f3eee4", bottom="#e2d6c0"):
        self.im = Image.new("RGB", (W * K, W * K))
        px = self.im.load()
        for y in range(W * K):
            row = mix(top, bottom, y / (W * K - 1))
            for x in range(W * K):
                px[x, y] = row
        self.d = ImageDraw.Draw(self.im, "RGBA")

    def s(self, v):
        return [(x * K, y * K) for x, y in v] if isinstance(v[0], (tuple, list)) else [c * K for c in v]

    def poly(self, pts, fill, outline=None, w=0):
        self.d.polygon(self.s(pts), fill=fill, outline=outline, width=w * K)

    def line(self, pts, fill, w=4):
        self.d.line(self.s(pts), fill=fill, width=int(w * K), joint="curve")

    def ell(self, box, fill, outline=None, w=0):
        self.d.ellipse(self.s(box), fill=fill, outline=outline, width=w * K)

    def rect(self, box, fill, r=0, outline=None, w=0):
        self.d.rounded_rectangle(self.s(box), radius=r * K, fill=fill, outline=outline, width=w * K)

    def text(self, xy, text, size, fill=INK, anchor="mm"):
        if not TEXT:
            return
        self.d.text((xy[0] * K, xy[1] * K), text, font=font(size), fill=fill, anchor=anchor)

    def shadow(self, box, alpha=50):
        layer = Image.new("RGBA", self.im.size, (0, 0, 0, 0))
        ImageDraw.Draw(layer).ellipse(self.s(box), fill=(60, 40, 20, alpha))
        self.im.paste(layer, (0, 0), layer)
        self.d = ImageDraw.Draw(self.im, "RGBA")

    def save(self, folder, name):
        os.makedirs(folder, exist_ok=True)
        final = self.im.resize((W, W), Image.LANCZOS)
        final.save(os.path.join(folder, name), "WEBP", quality=88, method=6)


# ───────────────────────── 합죽선 ─────────────────────────
def polar(cx, cy, r, deg):
    a = math.radians(deg)
    return (cx + r * math.sin(a), cy - r * math.cos(a))


def plum(c, x, y, size=26, rot=0):
    """매화: 꽃잎 다섯 장 + 꽃술."""
    for i in range(5):
        a = math.radians(rot + i * 72)
        px, py = x + size * 0.85 * math.sin(a), y - size * 0.85 * math.cos(a)
        c.ell((px - size * 0.62, py - size * 0.62, px + size * 0.62, py + size * 0.62), PLUM_PINK, PLUM_DEEP, 2)
    c.ell((x - size * 0.3, y - size * 0.3, x + size * 0.3, y + size * 0.3), "#f3c24f")
    for i in range(5):
        a = math.radians(rot + 36 + i * 72)
        c.line([(x, y), (x + size * 0.6 * math.sin(a), y - size * 0.6 * math.cos(a))], "#b5762a", 2)


def plum_branch(c, pts, blossoms, w=7):
    c.line(pts, BRANCH, w)
    for i in range(len(pts) - 1):  # 가지 마디
        x, y = pts[i]
        c.line([(x, y), (x + 14, y - 26)], BRANCH, max(2, w - 3))
    for (x, y, s, r) in blossoms:
        plum(c, x, y, s, r)


def fan(c, cx=600, cy=1010, spread=50, r_in=270, r_out=690, ribs=38, closed_ratio=1.0, plum_art=True, tassel=True):
    left, right = -spread, spread
    # 선면(한지) — 부채꼴 띠
    arc = [polar(cx, cy, r_out, left + (right - left) * i / 60) for i in range(61)]
    arc2 = [polar(cx, cy, r_in, right - (right - left) * i / 60) for i in range(61)]
    c.poly(arc + arc2, PAPER, BAMBOO_DARK, 3)
    # 살 사이 접힌 주름(음영)
    for i in range(ribs + 1):
        a = left + (right - left) * i / ribs
        shade = "#00000010" if i % 2 else "#ffffff25"
        a2 = left + (right - left) * (i + 1) / ribs
        c.poly([polar(cx, cy, r_in, a), polar(cx, cy, r_out, a), polar(cx, cy, r_out, a2), polar(cx, cy, r_in, a2)], shade)
    # 부챗살: 종이 아래로 드러나는 대나무 살(두 쪽 붙임 → 밝은/어두운 두 줄)
    for i in range(ribs + 1):
        a = left + (right - left) * i / ribs
        c.line([polar(cx, cy, 20, a), polar(cx, cy, r_out, a)], BAMBOO_DARK, 3)
        c.line([polar(cx, cy, 20, a + 0.35), polar(cx, cy, r_in, a + 0.35)], BAMBOO_LIGHT, 3)
    # 변죽(양 가장자리 굵은 살, 7쪽 붙임)
    for side in (left, right):
        for layer in range(7):
            off = (layer - 3) * 0.55 * (1 if side > 0 else 1)
            col = BAMBOO if layer % 2 == 0 else BAMBOO_LIGHT
            c.line([polar(cx, cy, 20, side + off), polar(cx, cy, r_out + 14, side + off)], col, 5)
        c.line([polar(cx, cy, 20, side - 2.2), polar(cx, cy, r_out + 14, side - 2.2)], BAMBOO_DARK, 3)
        c.line([polar(cx, cy, 20, side + 2.2), polar(cx, cy, r_out + 14, side + 2.2)], BAMBOO_DARK, 3)
        # 변죽에 새긴 매화
        for r in (420, 520, 620):
            x, y = polar(cx, cy, r, side)
            plum(c, x, y, 11, 10)
    # 종이 위 매화 그림
    if plum_art:
        plum_branch(c, [polar(cx, cy, 310, -38), polar(cx, cy, 430, -32), polar(cx, cy, 520, -19), polar(cx, cy, 610, -7),
                        polar(cx, cy, 660, 12)], [], 8)
        for (r, a, s, rot) in [(430, -32, 28, 5), (520, -19, 31, 20), (610, -7, 33, 40), (650, 11, 28, 12), (565, -26, 22, 33),
                               (480, -27, 20, 15), (370, -35, 20, 50)]:
            x, y = polar(cx, cy, r, a)
            plum(c, x, y, s, rot)
        for (r, a) in [(540, 8), (500, 20), (590, 24)]:
            x, y = polar(cx, cy, r, a)
            c.ell((x - 9, y - 9, x + 9, y + 9), PLUM_DEEP)
    # 사북(축)
    c.ell((cx - 22, cy - 22, cx + 22, cy + 22), BAMBOO_DARK, "#4a3414", 3)
    c.ell((cx - 9, cy - 9, cx + 9, cy + 9), "#d9c28a")
    if tassel:  # 선추(고리·매듭·술)
        c.line([(cx, cy + 20), (cx, cy + 70)], "#a63d40", 4)
        c.ell((cx - 18, cy + 66, cx + 18, cy + 102), "#5aa58f", "#2f6e5c", 3)
        c.ell((cx - 6, cy + 74, cx + 6, cy + 86), "#e6f3ee")
        for dx in (-14, -7, 0, 7, 14):
            c.line([(cx + dx * 0.4, cy + 100), (cx + dx, cy + 150)], "#a63d40", 3)


def closed_fan(c, cx, cy, length=760, angle=-28):
    """접은 부채: 살이 겹쳐 좁은 막대처럼 보인다. 별도 도화지에 세로로 그려 회전해 붙인다."""
    layer = Canvas("#00000000", "#00000000")
    layer.im = Image.new("RGBA", (W * K, W * K), (0, 0, 0, 0))
    layer.d = ImageDraw.Draw(layer.im, "RGBA")
    top, bot = 120, 120 + length
    layer.rect((560, top, 640, bot), BAMBOO, 20, BAMBOO_DARK, 3)
    for i in range(12):
        x = 566 + i * 6
        layer.line([(x, top + 10), (x, bot - 10)], BAMBOO_LIGHT if i % 2 else BAMBOO_DARK, 2)
    layer.rect((566, top + 14, 634, top + 380), PAPER, 10, BAMBOO_DARK, 2)
    for i in range(10):
        y = top + 30 + i * 34
        layer.line([(568, y), (632, y)], PAPER_SHADE, 3)
    plum(layer, 600, top + 120, 17, 10)
    plum(layer, 600, top + 250, 15, 30)
    layer.ell((574, bot - 38, 626, bot - 4), BAMBOO_DARK, "#4a3414", 3)
    layer.line([(600, bot - 10), (600, bot + 40)], "#a63d40", 4)
    layer.ell((582, bot + 36, 618, bot + 72), "#5aa58f", "#2f6e5c", 3)
    for dx in (-12, -6, 0, 6, 12):
        layer.line([(600 + dx * 0.4, bot + 70), (600 + dx, bot + 125)], "#a63d40", 3)
    rotated = layer.im.rotate(angle, resample=Image.BICUBIC, center=(600 * K, 500 * K))
    c.im.paste(rotated, ((cx - 600) * K, (cy - 500) * K), rotated)
    c.d = ImageDraw.Draw(c.im, "RGBA")


def caption(c, title, sub=None, y=1120):
    c.text((600, y), title, 40, INK)
    if sub:
        c.text((600, y + 44), sub, 26, "#6b6258")


def hap_hero():
    c = Canvas("#f6f1e6", "#e4d8c0")
    c.shadow((220, 990, 980, 1075), 55)
    fan(c)
    return c


def hap_packshot():
    c = Canvas("#eef0ee", "#d8dcd6")
    c.shadow((220, 1010, 980, 1090), 45)
    closed_fan(c, 600, 560, 760, -24)
    caption(c, "전주 합죽선 · 매화선", "접은 모습 — 선추(고리)와 사북", 1110)
    return c


def hap_detail_ribs():
    c = Canvas("#f4eee0", "#e2d4b6")
    # 부챗살 확대: 대나무 겉대 두 쪽을 붙인 단면과 살
    for i in range(14):
        x0 = 120 + i * 70
        c.rect((x0, 120, x0 + 52, 880), BAMBOO_LIGHT if i % 2 else BAMBOO, 8, BAMBOO_DARK, 3)
        c.line([(x0 + 26, 130), (x0 + 26, 870)], BAMBOO_DARK, 2)  # 두 쪽을 붙인 이음선
    c.rect((90, 880, 1110, 960), "#d9c28a", 10, BAMBOO_DARK, 3)
    c.ell((560, 600, 640, 680), BAMBOO_DARK, "#4a3414", 4)
    c.ell((585, 625, 615, 655), "#d9c28a")
    caption(c, "부챗살 확대", "얇게 깎은 대나무 두 쪽을 붙여 한 개의 살을 만듭니다", 1050)
    c.text((600, 1130), "합죽선 한 자루의 부챗살은 보통 38개", 28, "#6b6258")
    return c


def hap_lifestyle_gift():
    c = Canvas("#efe6da", "#d9c9ad")
    c.shadow((160, 980, 1040, 1070), 50)
    c.rect((170, 500, 1030, 960), "#8c3b3f", 18, "#5e2427", 4)  # 선물 상자
    c.rect((170, 440, 1030, 540), "#a34a4e", 14, "#5e2427", 4)  # 뚜껑
    c.rect((560, 440, 640, 960), "#d9b45a", 0)               # 리본
    c.rect((170, 700, 1030, 760), "#d9b45a", 0)
    c.ell((540, 380, 660, 460), "#d9b45a", "#a8852d", 4)
    c.poly([(600, 420), (470, 360), (470, 470)], "#e6c673", "#a8852d", 3)
    c.poly([(600, 420), (730, 360), (730, 470)], "#e6c673", "#a8852d", 3)
    caption(c, "선물 포장", "한지 포장 · 선물 상자", 1120)
    closed_fan(c, 600, 760, 560, -90)
    return c


def hap_plum_closeup():
    c = Canvas("#f5ecd7", "#eadcb6")
    for i in range(1, 20):
        c.line([(i * 60, 0), (i * 60, 1200)], "#ffffff40" if i % 2 else "#00000012", 6)  # 선면의 살 자국
    plum_branch(c, [(80, 1000), (260, 820), (420, 740), (600, 560), (760, 470), (960, 300), (1100, 220)],
                [(250, 830, 70, 8), (430, 740, 78, 25), (610, 560, 84, 40), (770, 470, 76, 12), (950, 310, 70, 30),
                 (330, 960, 54, 20), (520, 860, 50, 5)], 16)
    for (x, y) in [(120, 880), (690, 640), (860, 400), (1040, 280)]:
        c.ell((x - 18, y - 18, x + 18, y + 18), PLUM_DEEP, BRANCH, 3)
    caption(c, "선면 매화 그림", "닥나무 한지(선자지) 위에 한 획씩 그린 매화", 1110)
    return c


def hap_detail_edge():
    c = Canvas("#f1e7d0", "#e0cfa5")
    for layer in range(7):  # 변죽: 대나무 7쪽
        y = 200 + layer * 95
        c.rect((60, y, 1140, y + 78), BAMBOO if layer % 2 == 0 else BAMBOO_LIGHT, 14, BAMBOO_DARK, 3)
    for i in range(5):
        plum(c, 180 + i * 210, 590, 40, 10 + i * 9)
    caption(c, "변죽(가장자리 굵은 살)", "대나무 7쪽을 붙여 만들고 매화를 새깁니다", 1060)
    return c


def hap_process():
    """제작 과정 여섯 단계를 글자 없이 그림으로만 보여 준다(대나무 → 풀칠 → 살 다듬기 → 한지 → 매화 → 사북·선추)."""
    c = Canvas("#f3ede0", "#e0d2b4")
    for i in range(6):
        col, row = i % 3, i // 3
        x, y = 210 + col * 390, 330 + row * 400
        c.ell((x - 140, y - 140, x + 140, y + 140), PAPER, BAMBOO_DARK, 5)
        if i == 0:      # 대나무 마디
            c.rect((x - 36, y - 100, x + 36, y + 100), BAMBOO, 18, BAMBOO_DARK, 4)
            c.line([(x - 36, y - 20), (x + 36, y - 20)], BAMBOO_DARK, 5)
            c.line([(x - 36, y + 50), (x + 36, y + 50)], BAMBOO_DARK, 5)
        elif i == 1:    # 겉대 두 쪽 + 민어풀
            c.rect((x - 90, y - 70, x - 20, y + 90), BAMBOO_LIGHT, 10, BAMBOO_DARK, 3)
            c.rect((x + 20, y - 70, x + 90, y + 90), BAMBOO, 10, BAMBOO_DARK, 3)
            c.ell((x - 18, y - 110, x + 18, y - 70), "#e9dcc0", "#8b7b5d", 3)
        elif i == 2:    # 다듬은 살 묶음
            for k in range(-4, 5):
                c.line([(x + k * 14, y - 100), (x + k * 14 * 0.4, y + 100)], BAMBOO_DARK, 5)
        elif i == 3:    # 한지 붙이기
            c.poly([polar(x, y + 100, 190, -40 + j * 80 / 30) for j in range(31)] + [polar(x, y + 100, 80, 40 - j * 80 / 30) for j in range(31)], PAPER, BAMBOO_DARK, 3)
        elif i == 4:    # 매화 새기기
            plum(c, x, y, 48, 10)
            c.line([(x - 100, y + 70), (x - 30, y + 20)], BRANCH, 8)
        else:           # 사북과 선추
            c.ell((x - 20, y - 60, x + 20, y - 20), BAMBOO_DARK, "#4a3414", 3)
            c.line([(x, y - 20), (x, y + 30)], "#a63d40", 4)
            c.ell((x - 18, y + 26, x + 18, y + 62), "#5aa58f", "#2f6e5c", 3)
            for dx in (-10, -5, 0, 5, 10):
                c.line([(x + dx * 0.4, y + 60), (x + dx, y + 110)], "#a63d40", 3)
    return c


def hap_materials():
    c = Canvas("#f5efe2", "#e3d6bb")
    c.text((600, 110), "합죽선의 재료", 54, INK)
    # 대나무 마디
    for k, x in enumerate((200, 330)):
        c.rect((x, 280, x + 90, 800), BAMBOO if k == 0 else BAMBOO_LIGHT, 24, BAMBOO_DARK, 4)
        c.line([(x, 420), (x + 90, 420)], BAMBOO_DARK, 5)
        c.line([(x, 640), (x + 90, 640)], BAMBOO_DARK, 5)
    c.text((315, 860), "대나무(담양)", 32)
    # 한지 두루마리
    c.rect((520, 330, 820, 700), PAPER, 20, BAMBOO_DARK, 4)
    for i in range(7):
        c.line([(540, 370 + i * 45), (800, 370 + i * 45)], PAPER_SHADE, 4)
    c.text((670, 770), "닥나무 한지(선자지)", 32)
    # 민어풀 항아리
    c.poly([(900, 430), (1080, 430), (1120, 600), (1060, 740), (920, 740), (860, 600)], "#d9cdb6", "#8b7b5d", 4)
    c.rect((910, 400, 1070, 440), "#b7a584", 10, "#8b7b5d", 3)
    c.text((990, 800), "민어풀(부레풀)", 32)
    c.text((600, 1000), "대나무 쪽은 민어 부레를 곤 풀로 붙이고, 선면은 닥나무 한지를 바릅니다", 30, "#6b6258")
    return c


def hap_open_on_table():
    c = Canvas("#ece3d2", "#cdb994")
    c.rect((0, 880, 1200, 1200), "#a37b4d", 0)
    for i in range(7):
        c.line([(0, 900 + i * 45), (1200, 900 + i * 45)], "#00000018", 3)
    c.shadow((200, 960, 1000, 1030), 70)
    fan(c, cx=600, cy=960, spread=48, r_in=250, r_out=640, tassel=True)
    # 찻잔
    c.ell((900, 940, 1100, 990), "#00000025")
    c.poly([(920, 840), (1080, 840), (1060, 960), (940, 960)], "#f2efe6", "#9a9588", 3)
    c.ell((920, 825, 1080, 855), "#cfe3dc", "#9a9588", 3)
    return c


HAP = [("01-hero.webp", hap_hero), ("02-packshot.webp", hap_packshot), ("03-detail.webp", hap_detail_ribs),
       ("04-lifestyle.webp", hap_lifestyle_gift), ("05-lifestyle-02.webp", hap_open_on_table),
       ("06-detail-02.webp", hap_plum_closeup), ("07-detail-03.webp", hap_detail_edge),
       ("08-detail-04.webp", hap_process), ("09-detail-05.webp", hap_materials)]


# ───────────────────────── 청자 · 분청 찻잔 ─────────────────────────
CELADON, CELADON_DARK, CELADON_LIGHT = "#9fc7b6", "#5f8f7e", "#c9e2d6"
BUNJEONG, BUNJEONG_DARK, IRON = "#d8cdb5", "#8a7a5c", "#3a2c22"


def cup(c, cx, cy, w, h, body, edge, foot):
    """찻잔 옆모습: 위가 넓고 아래가 좁은 둥근 사발 + 굽. 반환값은 y 높이에서의 반폭 함수(무늬가 밖으로 나가지 않게)."""
    top_w, bot_w = w, w * 0.5
    top_y, bot_y = cy - h / 2, cy + h / 2

    def hw(y):
        t = min(1.0, max(0.0, (bot_y - y) / h))
        return (bot_w + (top_w - bot_w) * (t ** 0.62)) / 2

    left = [(cx - hw(top_y + h * i / 30), top_y + h * i / 30) for i in range(31)]
    right = [(cx + hw(bot_y - h * i / 30), bot_y - h * i / 30) for i in range(31)]
    c.poly(left + right, body, edge, 4)
    c.ell((cx - top_w / 2, top_y - 22, cx + top_w / 2, top_y + 22), mix(body, "#ffffff", 0.35), edge, 4)
    c.rect((cx - bot_w * 0.42, bot_y - 4, cx + bot_w * 0.42, bot_y + 30), foot, 6, edge, 3)
    return hw


def crane(c, x, y, s=1.0, col="#f7f4ea"):
    """운학문의 학(단순화)."""
    c.ell((x - 30 * s, y - 14 * s, x + 30 * s, y + 14 * s), col)
    c.line([(x + 24 * s, y - 6 * s), (x + 50 * s, y - 38 * s), (x + 60 * s, y - 36 * s)], col, 6 * s)
    c.poly([(x - 20 * s, y), (x - 70 * s, y - 36 * s), (x + 5 * s, y - 10 * s)], col)
    c.line([(x - 4 * s, y + 12 * s), (x - 12 * s, y + 50 * s)], col, 3 * s)
    c.line([(x + 8 * s, y + 12 * s), (x + 4 * s, y + 50 * s)], col, 3 * s)


def cloud(c, x, y, s=1.0, col="#f7f4ea"):
    for dx, r in ((-24, 14), (0, 20), (24, 14)):
        c.ell((x + dx * s - r * s, y - r * s, x + dx * s + r * s, y + r * s), col)
    c.rect((x - 36 * s, y, x + 36 * s, y + 12 * s), col, 6)


def cel_hero():
    c = Canvas("#eef2ee", "#d6ddd6")
    c.shadow((260, 900, 940, 990), 55)
    cup(c, 600, 620, 520, 380, CELADON, CELADON_DARK, CELADON_DARK)
    crane(c, 520, 600, 1.1)
    crane(c, 700, 660, 0.9)
    cloud(c, 620, 540, 1.1)
    cloud(c, 470, 700, 0.9)
    caption(c, "청자 운학문 찻잔", "비색 유약 · 상감 기법", 1110)
    return c


def bun_hero():
    c = Canvas("#f1ece1", "#ddd2bb")
    c.shadow((260, 900, 940, 990), 55)
    hw = cup(c, 600, 620, 520, 380, BUNJEONG, BUNJEONG_DARK, "#a89677")
    for i in range(9):  # 귀얄: 풀비로 쓸듯이 바른 백토 결
        y = 478 + i * 38
        m = hw(y) - 14
        c.line([(600 - m + (i % 3) * 6, y), (600 + m - (i % 2) * 10, y + 5)], "#f7f2e6", 12)
    for i in range(7):  # 철화: 철분 안료로 그린 풀꽃 선
        x = 600 + (i - 3) * 46
        c.line([(x, 700), (x + 6, 640), (x + 18, 610)], IRON, 4)
    caption(c, "분청사기 귀얄 찻잔", "백토 분장 · 철화", 1110)
    return c


def pair_on_tray():
    c = Canvas("#efe8dc", "#d8cbb0")
    c.rect((140, 800, 1060, 880), "#8a5e3b", 24, "#5e3d24", 4)
    c.shadow((200, 880, 1000, 960), 60)
    cup(c, 410, 620, 330, 240, CELADON, CELADON_DARK, CELADON_DARK)
    crane(c, 370, 610, 0.7)
    cloud(c, 450, 580, 0.7)
    hw = cup(c, 790, 620, 330, 240, BUNJEONG, BUNJEONG_DARK, "#a89677")
    for i in range(6):
        y = 545 + i * 28
        m = hw(y) - 10
        c.line([(790 - m, y), (790 + m, y + 5)], "#f7f2e6", 9)
    caption(c, "청자 · 분청 한 쌍", "소반 위의 다기", 1110)
    return c


def techniques():
    c = Canvas("#f3eee3", "#e0d6c0")
    c.text((600, 100), "분청사기의 장식 기법", 52, INK)
    items = [("상감", "무늬를 파고 백토를 메움"), ("인화", "도장으로 무늬를 반복해 찍음"),
             ("귀얄", "풀비로 백토를 쓸듯이 바름"), ("철화", "철분 안료로 붓그림")]
    for i, (t, s) in enumerate(items):
        x, y = 330 + (i % 2) * 540, 300 + (i // 2) * 470
        c.rect((x - 240, y - 130, x + 240, y + 130), BUNJEONG, 20, BUNJEONG_DARK, 4)
        if t == "상감":
            for j in range(4):
                c.line([(x - 180 + j * 90, y - 80), (x - 140 + j * 90, y + 40)], "#f7f2e6", 8)
            crane(c, x, y, 0.8, "#f7f2e6")
        elif t == "인화":
            for r in range(3):
                for q in range(7):
                    c.ell((x - 190 + q * 62 - 10, y - 90 + r * 60 - 10, x - 190 + q * 62 + 10, y - 90 + r * 60 + 10), "#f7f2e6")
        elif t == "귀얄":
            for r in range(6):
                c.line([(x - 200, y - 100 + r * 38), (x + 200 - (r % 2) * 20, y - 94 + r * 38)], "#f7f2e6", 14)
        else:
            c.line([(x - 150, y + 70), (x - 90, y - 70), (x - 20, y + 60), (x + 60, y - 80), (x + 150, y + 50)], IRON, 8)
        c.text((x, y + 175), t, 40, INK)
        c.text((x, y + 225), s, 24, "#6b6258")
    return c


def foot_detail():
    c = Canvas("#eef1ee", "#d5dcd5")
    c.shadow((240, 850, 960, 940), 50)
    c.ell((300, 360, 900, 520), CELADON, CELADON_DARK, 5)    # 위에서 본 안쪽
    c.ell((380, 395, 820, 490), CELADON_LIGHT, CELADON_DARK, 3)
    c.ell((560, 430, 640, 462), "#eef6f1", CELADON_DARK, 2)
    c.poly([(300, 440), (900, 440), (760, 780), (440, 780)], CELADON, CELADON_DARK, 5)
    c.rect((470, 780, 730, 840), CELADON_DARK, 10)
    caption(c, "찻잔의 안쪽과 굽", "비색 유약이 고르게 입혀진 모습", 1110)
    return c


def gift_box():
    c = Canvas("#f0e8dc", "#dccfb4")
    c.shadow((180, 960, 1020, 1050), 50)
    c.rect((200, 520, 1000, 940), "#5e7f72", 18, "#3b5249", 4)
    c.rect((200, 460, 1000, 560), "#6f9384", 14, "#3b5249", 4)
    c.rect((520, 460, 680, 940), "#e5dcc2", 0)
    cup(c, 420, 760, 220, 150, CELADON, CELADON_DARK, CELADON_DARK)
    cup(c, 780, 760, 220, 150, BUNJEONG, BUNJEONG_DARK, "#a89677")
    caption(c, "선물 상자", "청자 · 분청 찻잔 세트", 1120)
    return c


TEA = [("01-hero-celadon.webp", cel_hero), ("02-hero-bunjeong.webp", bun_hero), ("03-pair.webp", pair_on_tray),
       ("04-techniques.webp", techniques), ("05-detail-foot.webp", foot_detail), ("06-gift-set.webp", gift_box)]


def main():
    for folder, items in (("hapjukseon-maehwa-illustrated", HAP), ("cheongja-bunjeong-teacup", TEA)):
        for name, fn in items:
            fn().save(os.path.join(OUT, folder), name)
            print("저장:", folder, name)


if __name__ == "__main__":
    main()
