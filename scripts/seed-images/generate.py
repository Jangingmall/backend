#!/usr/bin/env python3
"""시드 상품 대표 이미지(소분류별 일러스트) 생성기.

외부 쇼핑몰 이미지는 링크가 끊기거나 상품과 무관하거나 안전성을 보장할 수 없어, 소분류(56종)마다 직접 그린
일러스트를 만들어 docs/seed-images/sub-NN.png 로 저장한다. 사람·실제 사진·상표가 없는 단순 도형 그림이라
부적절한 이미지가 섞일 수 없다. 필요: pip install pillow, 한글 글꼴(wqy-zenhei 등).

실행: python3 scripts/seed-images/generate.py
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

SIZE = 800
OUT = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "seed-images")
FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
    "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
    "/System/Library/Fonts/AppleSDGothicNeo.ttc",
]

# (소분류 id, 이름, 대분류 id, 그림 종류)
ITEMS = [
    (1, "다기·찻잔", 1, "teacup"), (2, "그릇·접시", 1, "plate"), (3, "수저·젓가락", 1, "spoon"),
    (4, "컵·술병·술잔", 1, "bottle"), (5, "소반·쟁반", 1, "tray"), (6, "칼·도마", 1, "board"),
    (7, "항아리·옹기", 1, "jar"), (8, "냄비·솥", 1, "pot"), (9, "제기", 1, "ritual"),
    (10, "조명", 2, "lamp"), (11, "수납함·보석함", 2, "box"), (12, "화병·꽃", 2, "vase"),
    (13, "시계·벽장식", 2, "clock"), (14, "발·가림막", 2, "blind"), (15, "가구", 2, "chest"),
    (16, "장석·손잡이", 2, "handle"), (17, "반지·귀걸이", 3, "ring"), (18, "목걸이·팔찌", 3, "necklace"),
    (19, "노리개·브로치", 3, "norigae"), (20, "머리핀·비녀", 3, "hairpin"), (21, "스카프·머플러", 3, "scarf"),
    (22, "가방·파우치", 3, "bag"), (23, "지갑·카드지갑", 3, "wallet"), (24, "키링·열쇠고리", 3, "keyring"),
    (25, "부채", 3, "fan"), (26, "한복·생활한복", 3, "hanbok"), (27, "갓·모자", 3, "gat"),
    (28, "망건·머리장식", 3, "headband"), (29, "붓·먹·벼루", 4, "brush"), (30, "한지·편지지", 4, "paper"),
    (31, "볼펜·만년필", 4, "pen"), (32, "명함집", 4, "cardcase"), (33, "필통·펜꽂이", 4, "pencup"),
    (34, "방석·보료", 5, "cushion"), (35, "침구·이불", 5, "bedding"), (36, "테이블보·러너", 5, "tablecloth"),
    (37, "보자기·복주머니", 5, "pouch"), (38, "손수건", 5, "handkerchief"), (39, "앞치마·주방패브릭", 5, "apron"),
    (40, "돗자리·왕골", 5, "mat"), (41, "자수 액자·소품", 5, "embroidery"), (42, "액자·그림", 6, "frame"),
    (43, "병풍·족자", 6, "screen"), (44, "서예·서각", 6, "calligraphy"), (45, "목조각·조형", 6, "carving"),
    (46, "전통인형·탈", 6, "mask"), (47, "장도·전통무구", 6, "dagger"), (48, "전통악기", 6, "gayageum"),
    (49, "불교용품", 6, "lotus"), (50, "소장품", 6, "pedestal"), (51, "전통주", 7, "liquor"),
    (52, "차", 7, "tea"), (53, "장·장아찌", 7, "jang"), (54, "김치", 7, "kimchi"),
    (55, "떡·한과", 7, "ricecake"), (56, "궁중음식·선물세트", 7, "giftbox"),
]

# 대분류별 (배경 위, 배경 아래, 포인트, 진한 색, 밝은 색)
PALETTE = {
    1: ("#f4efe6", "#e1d6c2", "#5b7c8d", "#2f4858", "#cfe0e8"),
    2: ("#f1ece4", "#ddd0bd", "#a8763e", "#5e3b1c", "#e8cfa5"),
    3: ("#f6eef0", "#e7d3d9", "#b0485f", "#6b2437", "#f1c5d0"),
    4: ("#eef0f2", "#d3d9df", "#3b4c63", "#1f2937", "#c6d2e2"),
    5: ("#f1f3ea", "#d8dec7", "#6d8a4b", "#374a24", "#d8e6c3"),
    6: ("#f3eeea", "#ddd0c8", "#8a5a44", "#4a2c1d", "#ecd1c2"),
    7: ("#f6f0e6", "#e6d6b9", "#b7822b", "#5d4210", "#f2dcaa"),
}
INK = "#2b2b2b"
CREAM = "#fffaf0"


def hexrgb(c):
    c = c.lstrip("#")
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))


def gradient(top, bottom):
    im = Image.new("RGB", (SIZE, SIZE))
    px = im.load()
    t, b = hexrgb(top), hexrgb(bottom)
    for y in range(SIZE):
        k = y / (SIZE - 1)
        row = tuple(int(t[i] + (b[i] - t[i]) * k) for i in range(3))
        for x in range(SIZE):
            px[x, y] = row
    return im


class Pen:
    """그림 영역(가운데 480x400 정도)에 도형을 그리는 도우미."""

    def __init__(self, d, accent, dark, light):
        self.d, self.a, self.k, self.l = d, accent, dark, light

    def ell(self, box, fill, outline=None, w=0):
        self.d.ellipse(box, fill=fill, outline=outline, width=w)

    def rect(self, box, fill, r=0, outline=None, w=0):
        if r:
            self.d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=w)
        else:
            self.d.rectangle(box, fill=fill, outline=outline, width=w)

    def poly(self, pts, fill, outline=None):
        self.d.polygon(pts, fill=fill, outline=outline)

    def line(self, pts, fill, w=6):
        self.d.line(pts, fill=fill, width=w, joint="curve")

    def arc(self, box, a0, a1, fill, w=6):
        self.d.arc(box, a0, a1, fill=fill, width=w)


def draw(kind, p):  # noqa: C901 - 그림 종류별 분기
    a, k, l = p.a, p.k, p.l
    cx, cy = 400, 330
    if kind == "teacup":
        p.ell((250, 470, 550, 510), "#00000022")
        p.poly([(280, 300), (520, 300), (490, 450), (310, 450)], l, k)
        p.ell((280, 285, 520, 320), CREAM, k, 4)
        p.arc((470, 320, 570, 420), -70, 90, k, 10)
        p.rect((340, 450, 460, 475), a, 8)
        p.line([(350, 240), (370, 190), (350, 140)], "#9aa7ad", 6)
        p.line([(420, 250), (440, 195), (420, 150)], "#9aa7ad", 6)
    elif kind == "plate":
        p.ell((170, 270, 630, 470), l, k, 5)
        p.ell((240, 305, 560, 440), CREAM, a, 4)
        p.ell((330, 340, 470, 410), a)
    elif kind == "spoon":
        p.ell((300, 150, 400, 300), l, k, 5)
        p.rect((340, 290, 360, 560), k, 8)
        p.rect((450, 180, 470, 560), l, 8, k, 3)
        p.rect((500, 180, 520, 560), l, 8, k, 3)
    elif kind == "bottle":
        p.rect((350, 160, 450, 220), a, 10)
        p.poly([(360, 220), (440, 220), (500, 330), (500, 520), (300, 520), (300, 330)], l, k)
        p.rect((320, 400, 480, 500), a, 8)
        p.ell((560, 420, 660, 440), "#00000022")
        p.poly([(570, 330), (650, 330), (635, 420), (585, 420)], CREAM, k)
    elif kind == "tray":
        p.rect((190, 330, 610, 380), l, 14, k, 5)
        p.rect((250, 380, 280, 520), k, 6)
        p.rect((520, 380, 550, 520), k, 6)
        p.ell((290, 240, 400, 330), a)
        p.ell((410, 255, 510, 330), CREAM, k, 4)
    elif kind == "board":
        p.rect((200, 340, 520, 500), "#c89a5b", 26, k, 5)
        p.rect((510, 395, 610, 445), "#c89a5b", 20, k, 5)
        p.ell((560, 410, 590, 430), CREAM, k, 3)
        for y in (370, 410, 450, 480):
            p.line([(230, y), (490, y + 6)], "#a67a3f", 3)
        p.rect((175, 262, 285, 300), "#6b4a2b", 14, k, 4)
        p.ell((205, 275, 217, 287), "#e3c76d")
        p.ell((240, 275, 252, 287), "#e3c76d")
        p.poly([(285, 262), (500, 250), (545, 272), (500, 300), (285, 300)], "#cfd8de", k)
        p.line([(290, 296), (505, 296)], "#8fa0aa", 4)
    elif kind == "jar":
        p.poly([(310, 190), (490, 190), (470, 230), (540, 330), (520, 480), (440, 540), (360, 540), (280, 480), (260, 330), (330, 230)], a, k)
        p.rect((300, 160, 500, 200), l, 10, k, 4)
        p.arc((300, 300, 500, 460), 200, 340, l, 8)
    elif kind == "pot":
        p.rect((190, 330, 610, 460), a, 60, k, 5)
        p.rect((190, 300, 610, 340), l, 14, k, 4)
        p.rect((350, 255, 450, 300), k, 18)
        p.ell((130, 345, 200, 395), l, k, 4)
        p.ell((600, 345, 670, 395), l, k, 4)
    elif kind == "ritual":
        p.ell((280, 170, 520, 250), l, k, 5)
        p.rect((375, 250, 425, 420), a, 8)
        p.ell((290, 405, 510, 470), l, k, 5)
        p.ell((340, 190, 460, 235), CREAM)
    elif kind == "lamp":
        p.poly([(300, 230), (500, 230), (560, 400), (240, 400)], "#fff1c2", k)
        p.rect((385, 400, 415, 500), k, 6)
        p.ell((310, 495, 490, 540), a)
        p.line([(400, 150), (400, 230)], k, 6)
        for sx in (-90, 0, 90):
            p.line([(400 + sx // 3, 260), (400 + sx, 390)], "#e3c76d", 4)
    elif kind == "box":
        p.rect((220, 330, 580, 500), a, 24, k, 5)
        p.poly([(220, 330), (580, 330), (540, 250), (260, 250)], l, k)
        p.ell((375, 370, 425, 420), CREAM, k, 4)
        p.ell((560, 215, 600, 255), "#ffffff", k, 3)
    elif kind == "vase":
        p.poly([(340, 330), (460, 330), (500, 450), (460, 540), (340, 540), (300, 450)], l, k)
        p.line([(400, 330), (400, 200)], "#4f7d3c", 7)
        p.line([(400, 300), (330, 230)], "#4f7d3c", 7)
        p.line([(400, 290), (480, 220)], "#4f7d3c", 7)
        for (fx, fy, fc) in ((400, 180, "#e86f91"), (320, 215, "#f2a65a"), (485, 205, "#e86f91")):
            for ang in range(0, 360, 72):
                import math
                p.ell((fx + 28 * math.cos(math.radians(ang)) - 20, fy + 28 * math.sin(math.radians(ang)) - 20,
                       fx + 28 * math.cos(math.radians(ang)) + 20, fy + 28 * math.sin(math.radians(ang)) + 20), fc)
            p.ell((fx - 14, fy - 14, fx + 14, fy + 14), "#ffe08a")
    elif kind == "clock":
        p.ell((250, 190, 550, 490), l, k, 10)
        p.ell((280, 220, 520, 460), CREAM, a, 4)
        p.line([(400, 340), (400, 260)], k, 8)
        p.line([(400, 340), (460, 370)], k, 8)
        p.ell((388, 328, 412, 352), a)
    elif kind == "blind":
        p.rect((190, 170, 610, 198), k, 8)
        for x in range(205, 600, 22):
            p.rect((x, 198, x + 15, 450), l, 4, a, 2)
        p.line([(190, 280), (610, 280)], a, 5)
        p.line([(190, 370), (610, 370)], a, 5)
        p.rect((190, 440, 610, 490), l, 24, k, 4)
        for x in range(215, 600, 40):
            p.line([(x, 445), (x, 485)], a, 3)
        for cx in (230, 570):
            p.line([(cx, 198), (cx, 510)], "#c0392b", 4)
            p.ell((cx - 10, 505, cx + 10, 525), "#c0392b")
    elif kind == "chest":
        p.rect((220, 240, 580, 470), a, 10, k, 6)
        p.line([(400, 240), (400, 470)], k, 5)
        p.line([(220, 355), (580, 355)], k, 5)
        p.ell((375, 285, 425, 335), l, k, 4)
        p.ell((375, 375, 425, 425), l, k, 4)
        p.rect((240, 470, 270, 520), k, 4)
        p.rect((530, 470, 560, 520), k, 4)
    elif kind == "handle":
        p.rect((200, 190, 600, 520), "#c89a5b", 8, k, 5)
        p.line([(400, 190), (400, 520)], k, 4)
        for y in (260, 340, 420):
            p.line([(215, y), (385, y + 4)], "#a67a3f", 3)
            p.line([(415, y + 4), (585, y)], "#a67a3f", 3)
        p.poly([(400, 235), (475, 285), (440, 335), (480, 385), (400, 440), (320, 385), (360, 335), (325, 285)], "#d9b44a", k)
        p.ell((372, 322, 428, 378), "#c89a5b", "#8a6a1f", 6)
        p.ell((388, 338, 412, 362), "#8a6a1f")
        for (x, y) in ((345, 285), (455, 285), (345, 390), (455, 390)):
            p.ell((x - 8, y - 8, x + 8, y + 8), "#8a6a1f")
    elif kind == "ring":
        p.ell((250, 300, 420, 470), None, a, 18)
        p.poly([(335, 285), (365, 250), (305, 250)], "#9ad1ff", k)
        p.ell((470, 330, 520, 400), l, k, 4)
        p.line([(495, 320), (495, 330)], k, 4)
        p.ell((540, 330, 590, 400), l, k, 4)
    elif kind == "necklace":
        p.arc((230, 130, 570, 470), 20, 160, a, 10)
        p.ell((375, 440, 425, 490), l, k, 5)
        p.ell((388, 452, 412, 478), a)
        for i in range(8):
            import math
            ang = math.radians(25 + i * 18)
            x, y = 400 + 170 * math.cos(ang), 300 + 170 * math.sin(ang)
            p.ell((x - 9, y - 9, x + 9, y + 9), l, k, 2)
    elif kind == "norigae":
        p.ell((360, 150, 440, 230), a, k, 4)
        p.line([(400, 230), (400, 310)], k, 6)
        p.poly([(400, 300), (470, 360), (400, 420), (330, 360)], l, k)
        for dx in (-30, 0, 30):
            p.line([(400, 420), (400 + dx, 540)], a, 8)
        p.ell((380, 340, 420, 380), a)
    elif kind == "hairpin":
        p.line([(250, 480), (550, 220)], a, 14)
        p.ell((520, 170, 620, 270), l, k, 5)
        p.ell((545, 195, 595, 245), a)
        p.line([(250, 480), (240, 495)], k, 10)
        p.line([(300, 500), (500, 400)], "#c9d3d9", 8)
    elif kind == "scarf":
        import math
        top = [(200 + i * 10, 240 + 20 * math.sin(i * 0.45)) for i in range(41)]
        bot = [(600 - i * 10, 330 + 20 * math.sin((40 - i) * 0.45)) for i in range(41)]
        p.poly(top + bot, a, k)
        for i in range(2, 40, 4):
            x = 200 + i * 10
            p.line([(x, 240 + 20 * math.sin(i * 0.45)), (x, 330 + 20 * math.sin(i * 0.45))], l, 8)
        p.poly([(470, 345), (560, 355), (540, 500), (450, 490)], a, k)
        for i in range(0, 90, 10):
            p.line([(455 + i, 492 - (i // 9)), (452 + i, 535)], l, 4)
        for i in range(0, 90, 12):
            p.line([(200, 250 + i // 2 + 0), (190, 260 + i // 2)], l, 4)
    elif kind == "bag":
        p.arc((300, 170, 500, 330), 180, 360, k, 12)
        p.rect((230, 270, 570, 500), a, 40, k, 5)
        p.rect((230, 270, 570, 340), l, 40, k, 4)
        p.ell((375, 335, 425, 385), CREAM, k, 4)
    elif kind == "wallet":
        p.rect((220, 260, 580, 470), a, 24, k, 5)
        p.rect((220, 260, 580, 340), l, 24, k, 4)
        p.rect((500, 340, 590, 410), CREAM, 18, k, 4)
        p.ell((530, 360, 560, 390), a)
        p.rect((270, 410, 440, 435), l, 6)
    elif kind == "keyring":
        p.ell((340, 150, 460, 270), None, k, 10)
        p.line([(400, 270), (400, 340)], k, 6)
        p.rect((350, 340, 450, 470), a, 20, k, 5)
        p.ell((385, 380, 415, 410), CREAM, k, 3)
        p.line([(470, 300), (520, 360)], l, 8)
        p.ell((505, 350, 545, 390), l, k, 4)
    elif kind == "fan":
        import math
        for i in range(9):
            ang0 = 200 + i * 15
            p.d.pieslice((180, 230, 620, 670), ang0, ang0 + 15, fill=(a if i % 2 else l), outline=k, width=3)
        p.ell((375, 430, 425, 480), k)
        p.rect((385, 450, 415, 540), k, 6)
    elif kind == "hanbok":
        p.poly([(310, 190), (490, 190), (500, 280), (300, 280)], l, k)
        p.poly([(340, 190), (400, 250), (460, 190)], CREAM, k)
        p.rect((395, 215, 405, 300), a, 4)
        p.poly([(300, 300), (500, 300), (570, 530), (230, 530)], a, k)
        p.line([(230, 530), (570, 530)], k, 6)
        p.rect((300, 280, 500, 310), k, 4)
    elif kind == "gat":
        p.ell((200, 380, 600, 450), k)
        p.ell((300, 240, 500, 400), "#2a2a2a", k, 3)
        p.ell((230, 360, 570, 440), "#3a3a3a", k, 4)
        p.line([(330, 420), (320, 500)], a, 6)
        p.line([(470, 420), (480, 500)], a, 6)
    elif kind == "headband":
        p.ell((300, 190, 500, 420), "#e2c9a1", k, 5)
        p.rect((370, 410, 430, 510), "#c9ad82", 8, k, 4)
        p.ell((310, 500, 490, 540), "#c9ad82", k, 4)
        p.rect((296, 270, 504, 330), "#3a3a3a", 16, k, 4)
        for x in range(306, 500, 14):
            p.line([(x, 276), (x + 7, 324)], "#6d6d6d", 2)
            p.line([(x + 7, 276), (x, 324)], "#6d6d6d", 2)
        for x in (320, 480):
            p.ell((x - 14, 286, x + 14, 314), None, "#d9b44a", 5)
        p.ell((384, 280, 416, 322), "#e8a33d", k, 3)
    elif kind == "brush":
        p.line([(250, 520), (470, 260)], "#8a5a2b", 16)
        p.poly([(470, 260), (500, 200), (540, 170), (520, 230), (490, 270)], INK)
        p.rect((520, 400, 640, 480), "#2f3a45", 10, k, 4)
        p.ell((540, 405, 620, 435), INK)
    elif kind == "paper":
        p.rect((240, 190, 560, 500), CREAM, 6, k, 4)
        for y in range(240, 470, 36):
            p.line([(280, y), (520, y)], l, 4)
        p.rect((280, 215, 330, 235), a, 4)
        p.poly([(560, 190), (560, 250), (500, 190)], l, k)
    elif kind == "pen":
        p.poly([(240, 480), (560, 200), (590, 230), (270, 510)], a, k)
        p.poly([(240, 480), (270, 510), (225, 535)], INK)
        p.line([(520, 240), (550, 270)], l, 8)
        p.rect((560, 190, 600, 215), l, 4)
    elif kind == "cardcase":
        p.rect((230, 280, 570, 470), a, 20, k, 5)
        p.rect((230, 280, 570, 340), l, 20, k, 4)
        p.rect((290, 220, 510, 290), CREAM, 8, k, 3)
        p.line([(310, 245), (480, 245)], l, 5)
        p.line([(310, 265), (430, 265)], l, 5)
    elif kind == "pencup":
        p.rect((290, 310, 510, 500), a, 20, k, 5)
        p.line([(330, 310), (310, 190)], "#e3c76d", 14)
        p.line([(380, 310), (400, 170)], "#d9534f", 14)
        p.line([(430, 310), (470, 200)], "#4f7d3c", 14)
        p.line([(470, 310), (540, 230)], k, 14)
        p.rect((300, 380, 500, 410), l, 6)
    elif kind == "cushion":
        p.rect((230, 270, 570, 470), a, 70, k, 5)
        p.rect((300, 330, 500, 410), l, 36, k, 3)
        p.line([(400, 280), (400, 460)], k, 3)
        for (x, y) in ((250, 290), (550, 290), (250, 450), (550, 450)):
            p.ell((x - 10, y - 10, x + 10, y + 10), l, k, 3)
    elif kind == "bedding":
        p.rect((200, 420, 600, 505), a, 30, k, 5)
        p.rect((220, 350, 580, 430), l, 30, k, 4)
        p.rect((240, 280, 560, 360), CREAM, 30, k, 4)
        for x in range(255, 580, 70):
            p.ell((x, 440, x + 34, 474), l)
        for x in range(265, 560, 60):
            p.ell((x, 372, x + 26, 398), a)
        p.rect((300, 195, 500, 290), CREAM, 40, k, 4)
        p.line([(330, 245), (470, 245)], l, 5)
        p.ell((385, 215, 415, 245), a)
    elif kind == "tablecloth":
        p.rect((190, 300, 610, 340), a, 6, k, 4)
        p.poly([(190, 340), (610, 340), (640, 470), (160, 470)], l, k)
        for x in range(200, 620, 60):
            p.poly([(x, 470), (x + 30, 470), (x + 15, 500)], a)
        p.rect((350, 230, 450, 300), CREAM, 14, k, 3)
    elif kind == "pouch":
        p.poly([(260, 280), (540, 280), (580, 500), (220, 500)], a, k)
        p.poly([(260, 280), (400, 190), (540, 280)], l, k)
        p.ell((375, 260, 425, 310), CREAM, k, 4)
        p.line([(400, 310), (400, 380)], l, 6)
    elif kind == "handkerchief":
        p.poly([(250, 220), (560, 250), (540, 520), (230, 490)], l, k)
        p.rect((290, 270, 510, 470), None, 0, a, 6)
        p.ell((370, 340, 430, 400), a)
        p.poly([(560, 250), (590, 300), (540, 290)], a)
    elif kind == "apron":
        p.poly([(330, 180), (470, 180), (500, 280), (560, 520), (240, 520), (300, 280)], a, k)
        p.rect((340, 330, 460, 420), l, 12, k, 3)
        p.arc((330, 90, 470, 270), 180, 360, k, 8)
        p.line([(300, 280), (230, 300)], k, 8)
        p.line([(500, 280), (570, 300)], k, 8)
    elif kind == "mat":
        p.poly([(220, 230), (560, 230), (600, 500), (180, 500)], l, k)
        for y in range(250, 500, 28):
            p.line([(220 - (y - 230) // 7, y), (560 + (y - 230) // 7, y)], a, 4)
        p.poly([(220, 230), (180, 500), (150, 480), (190, 215)], a, k)
    elif kind == "embroidery":
        p.rect((220, 190, 580, 500), "#b08a5a", 6, k, 4)
        p.rect((250, 220, 550, 470), CREAM, 4, k, 3)
        p.line([(400, 430), (400, 320)], "#4f7d3c", 6)
        p.ell((340, 280, 400, 340), "#e86f91", k, 3)
        p.ell((400, 270, 460, 330), "#f2a65a", k, 3)
        p.ell((370, 300, 430, 360), "#f08fa5", k, 3)
    elif kind == "frame":
        p.rect((220, 190, 580, 500), "#a8763e", 8, k, 5)
        p.rect((255, 225, 545, 465), "#dfeaf0", 4, k, 3)
        p.poly([(255, 465), (360, 330), (440, 410), (500, 350), (545, 400), (545, 465)], "#6d8a4b")
        p.ell((460, 255, 510, 305), "#f2c14e")
    elif kind == "screen":
        for i in range(4):
            x0 = 200 + i * 105
            p.rect((x0, 210, x0 + 100, 490), CREAM if i % 2 else l, 4, k, 4)
            p.ell((x0 + 30, 260, x0 + 70, 300), a)
            p.line([(x0 + 50, 300), (x0 + 50, 420)], a, 5)
    elif kind == "calligraphy":
        p.rect((280, 160, 520, 185), "#6b4a2b", 6, k, 3)
        p.rect((300, 185, 500, 520), CREAM, 4, k, 4)
        p.rect((290, 515, 510, 540), "#6b4a2b", 6, k, 3)
        p.line([(345, 250), (455, 215)], INK, 24)
        p.line([(400, 225), (385, 330)], INK, 22)
        p.line([(335, 335), (470, 345)], INK, 20)
        p.line([(380, 340), (350, 440)], INK, 20)
        p.line([(405, 350), (455, 440)], INK, 20)
        p.rect((450, 455, 492, 497), "#c0392b", 4)
    elif kind == "carving":
        p.rect((230, 465, 570, 525), "#7a5230", 10, k, 4)
        p.poly([(285, 395), (210, 345), (250, 425)], "#a8743d", k)
        p.ell((270, 330, 520, 470), "#b07a42", k, 5)
        p.poly([(300, 410), (400, 340), (470, 380), (410, 450)], "#cf9d62", k)
        p.line([(330, 405), (430, 385)], "#a8743d", 4)
        p.ell((465, 270, 555, 360), "#b07a42", k, 5)
        p.poly([(548, 305), (600, 318), (548, 335)], "#d9822b", k)
        p.ell((505, 296, 521, 312), INK)
        p.line([(380, 470), (370, 490)], k, 5)
        p.line([(430, 470), (440, 490)], k, 5)
    elif kind == "mask":
        p.ell((260, 170, 540, 500), CREAM, k, 6)
        p.ell((310, 280, 370, 330), INK)
        p.ell((430, 280, 490, 330), INK)
        p.arc((330, 360, 470, 450), 20, 160, "#c0392b", 10)
        p.ell((375, 330, 425, 365), a)
        p.arc((300, 230, 380, 270), 200, 340, k, 8)
        p.arc((420, 230, 500, 270), 200, 340, k, 8)
    elif kind == "dagger":
        p.poly([(230, 480), (470, 300), (490, 325), (260, 500)], "#c9d3d9", k)
        p.rect((470, 270, 600, 340), a, 14, k, 5)
        p.rect((590, 285, 640, 325), l, 10, k, 4)
        p.line([(520, 300), (570, 300)], l, 5)
    elif kind == "gayageum":
        p.poly([(180, 330), (620, 290), (640, 380), (170, 400)], a, k)
        for i, y in enumerate(range(318, 390, 12)):
            p.line([(190, y + 8), (620, y - 24)], l, 3)
        for x in range(260, 600, 50):
            p.poly([(x, 300 + (x - 260) // -12), (x + 14, 300 + (x - 260) // -12), (x + 7, 380)], "#ffffff", k)
        p.rect((170, 400, 640, 430), k, 8)
    elif kind == "lotus":
        for ang, (dx, dy) in zip((-60, -30, 0, 30, 60), ((-120, 20), (-70, -20), (0, -40), (70, -20), (120, 20))):
            p.ell((360 + dx, 280 + dy, 440 + dx, 440 + dy), "#f6b6c8" if ang % 60 == 0 else "#f9cfdb", k, 4)
        p.ell((330, 380, 470, 470), "#ffe08a", k, 4)
        p.ell((200, 470, 600, 530), "#6fa56a")
        p.line([(400, 470), (400, 540)], "#4f7d3c", 8)
    elif kind == "pedestal":
        p.rect((240, 460, 560, 525), l, 10, k, 5)
        p.rect((290, 410, 510, 465), a, 8, k, 4)
        p.ell((300, 190, 500, 410), "#e4f1fc")
        p.poly([(380, 300), (420, 300), (445, 345), (425, 395), (375, 395), (355, 345)], "#9fc7b6", k)
        p.rect((375, 285, 425, 305), "#9fc7b6", 6, k, 3)
        p.ell((300, 190, 500, 410), None, "#6fa3d1", 5)
        p.rect((296, 392, 504, 416), "#6fa3d1", 6)
        p.arc((330, 220, 400, 300), 200, 280, "#ffffff", 6)
    elif kind == "liquor":
        p.poly([(360, 160), (440, 160), (450, 260), (520, 340), (520, 510), (280, 510), (280, 340), (350, 260)], l, k)
        p.rect((350, 130, 450, 175), a, 10)
        p.rect((300, 370, 500, 470), CREAM, 10, k, 3)
        p.ell((370, 395, 430, 445), a)
    elif kind == "tea":
        p.poly([(250, 320), (550, 320), (510, 480), (290, 480)], l, k)
        p.ell((250, 305, 550, 340), "#8bb36b", k, 4)
        p.poly([(330, 290), (400, 230), (470, 290)], "#6fa56a", k)
        p.line([(400, 230), (400, 330)], "#4f7d3c", 4)
        p.ell((560, 400, 660, 440), "#00000022")
        p.poly([(580, 320), (640, 320), (630, 410), (590, 410)], a, k)
    elif kind == "jang":
        p.poly([(300, 270), (500, 270), (540, 420), (500, 520), (300, 520), (260, 420)], "#8a5a2b", k)
        p.rect((280, 230, 520, 285), l, 10, k, 4)
        p.rect((330, 215, 470, 240), a, 6)
        p.arc((320, 340, 480, 480), 200, 340, l, 8)
        p.rect((310, 220, 490, 232), CREAM, 4)
    elif kind == "kimchi":
        p.poly([(290, 290), (510, 290), (550, 440), (500, 520), (300, 520), (250, 440)], "#9c4a2e", k)
        p.rect((270, 250, 530, 300), l, 10, k, 4)
        p.ell((310, 215, 490, 280), "#e0502c", k, 4)
        for (x, y) in ((340, 235), (400, 225), (450, 240)):
            p.ell((x - 18, y - 10, x + 18, y + 14), "#f58a57")
    elif kind == "ricecake":
        p.rect((220, 380, 580, 470), "#fffaf0", 12, k, 4)
        for i, c in enumerate(("#f6b6c8", "#fff3c4", "#bfe3b4", "#ffffff")):
            p.rect((250 + i * 80, 290, 310 + i * 80, 390), c, 14, k, 4)
        p.rect((230, 465, 570, 495), a, 8)
        p.ell((420, 195, 480, 255), "#f2a65a", k, 3)
    elif kind == "giftbox":
        p.rect((220, 330, 580, 510), a, 14, k, 5)
        p.rect((200, 270, 600, 340), l, 14, k, 5)
        p.rect((375, 270, 425, 510), "#c0392b")
        p.poly([(400, 270), (330, 200), (370, 190), (400, 250)], "#c0392b", k)
        p.poly([(400, 270), (470, 200), (430, 190), (400, 250)], "#c0392b", k)
        p.ell((380, 235, 420, 275), "#9c2b20", k, 3)
    else:
        raise ValueError(kind)


def make(font_path, sub_id, name, cat_id, kind):
    top, bottom, accent, dark, light = PALETTE[cat_id]
    im = gradient(top, bottom).convert("RGBA")
    overlay = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    # 바닥 그림자와 큰 원형 무대
    d.ellipse((120, 140, 680, 620), fill=(255, 255, 255, 110))
    pen = Pen(d, accent, dark, light)
    draw(kind, pen)
    im.alpha_composite(overlay)
    d2 = ImageDraw.Draw(im)
    # 하단 이름띠
    d2.rectangle((0, 650, SIZE, SIZE), fill=hexrgb(dark) + (255,))
    font = ImageFont.truetype(font_path, 56)
    small = ImageFont.truetype(font_path, 22)
    w = d2.textlength(name, font=font)
    d2.text(((SIZE - w) / 2, 668), name, font=font, fill=CREAM)
    note = "MIDAM · 시연용 일러스트"
    w2 = d2.textlength(note, font=small)
    d2.text(((SIZE - w2) / 2, 745), note, font=small, fill=hexrgb(light))
    return im.convert("RGB")


def main():
    font_path = next((f for f in FONT_CANDIDATES if os.path.exists(f)), None)
    if not font_path:
        sys.exit("한글 글꼴을 찾을 수 없습니다. FONT_CANDIDATES에 경로를 추가하세요.")
    os.makedirs(OUT, exist_ok=True)
    for sub_id, name, cat_id, kind in ITEMS:
        img = make(font_path, sub_id, name, cat_id, kind)
        img.save(os.path.join(OUT, f"sub-{sub_id:02d}.png"), optimize=True)
        # 업로드 규격과 같은 WebP(≤10MB, ≤10,000px)도 함께 만든다. 외부 링크로 서비스할 때 이 파일을 쓴다.
        img.save(os.path.join(OUT, f"sub-{sub_id:02d}.webp"), "WEBP", quality=88, method=6)
    print(f"{len(ITEMS)}개 생성 -> {os.path.abspath(OUT)}")


if __name__ == "__main__":
    main()
