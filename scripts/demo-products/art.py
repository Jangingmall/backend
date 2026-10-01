"""시연 상품 일러스트용 소재·무늬 그리기 도구 (글자 없음).

상품 설명은 따로 텍스트로 있으므로 이미지에는 글자를 넣지 않는다. 대신 소재(모시·누비·나전·칠보 …)와 색, 무늬를
그림으로 보여 사람이 설명과 맞는지 눈으로 해석할 수 있게 한다.
"""
import math
import random

from PIL import Image, ImageDraw

W = 800


def rgb(color, alpha=255):
    color = color.lstrip("#")
    return tuple(int(color[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)


def mix(a, b, t):
    a, b = rgb(a), rgb(b)
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def flower(d, x, y, size, petal, center="#f3c24f", edge=None, rot=0):
    for i in range(5):
        a = math.radians(rot + i * 72)
        px, py = x + size * 0.85 * math.sin(a), y - size * 0.85 * math.cos(a)
        d.ellipse((px - size * 0.62, py - size * 0.62, px + size * 0.62, py + size * 0.62), fill=rgb(petal),
                  outline=rgb(edge) if edge else None, width=2)
    d.ellipse((x - size * 0.3, y - size * 0.3, x + size * 0.3, y + size * 0.3), fill=rgb(center))


# ── 소재·무늬 ─────────────────────────────────────────────────────────────
def weave(d, box, light, dark, seed=1):
    """모시·무명·명주: 가는 날실과 씨실이 교차하는 결."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(light))
    for y in range(int(y0), int(y1), 6):
        d.line((x0, y, x1, y), fill=rgb(dark, 70 + rnd.randint(0, 40)), width=2)
    for x in range(int(x0), int(x1), 6):
        d.line((x, y0, x, y1), fill=rgb(dark, 55 + rnd.randint(0, 40)), width=2)
    for _ in range(int((x1 - x0) * (y1 - y0) / 1800)):  # 올이 살짝 굵어진 자리
        x, y = rnd.randint(int(x0), int(x1)), rnd.randint(int(y0), int(y1))
        d.line((x, y, x + rnd.randint(10, 26), y), fill=rgb("#ffffff", 120), width=2)


def quilt(d, box, light, dark, seed=1):
    """누비: 마름모 박음질 선과 잔 땀."""
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(light))
    step = 56
    for k in range(-20, 40):
        d.line((x0 + k * step, y0, x0 + k * step + (y1 - y0), y1), fill=rgb(dark, 190), width=3)
        d.line((x0 + k * step, y1, x0 + k * step + (y1 - y0), y0), fill=rgb(dark, 190), width=3)
    for y in range(int(y0) + step // 2, int(y1), step):
        for x in range(int(x0) + step // 2, int(x1), step):
            d.ellipse((x - 3, y - 3, x + 3, y + 3), fill=rgb(dark, 120))


def patchwork(d, box, colors, seed=1):
    """조각보: 크기가 다른 천 조각을 이어 붙인 모양."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    xs = [x0]
    while xs[-1] < x1:
        xs.append(xs[-1] + rnd.randint(110, 190))
    ys = [y0]
    while ys[-1] < y1:
        ys.append(ys[-1] + rnd.randint(110, 190))
    for i in range(len(xs) - 1):
        for j in range(len(ys) - 1):
            d.rectangle((xs[i], ys[j], min(xs[i + 1], x1), min(ys[j + 1], y1)), fill=rgb(rnd.choice(colors)),
                        outline=rgb("#ffffff", 220), width=5)
            d.rectangle((xs[i] + 14, ys[j] + 14, min(xs[i + 1], x1) - 14, min(ys[j + 1], y1) - 14),
                        outline=rgb("#ffffff", 90), width=2)


def plum(d, box, base, petal="#f6d4dc", deep="#d98aa0", seed=2):
    """매화 자수: 가지와 매화가 흩어진 천."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(base))
    pts = [(x0 + 20, y1 - 60), (x0 + (x1 - x0) * 0.3, y1 - (y1 - y0) * 0.35), (x0 + (x1 - x0) * 0.55, y0 + (y1 - y0) * 0.45),
           (x0 + (x1 - x0) * 0.8, y0 + (y1 - y0) * 0.25), (x1 - 20, y0 + 30)]
    d.line(pts, fill=rgb("#5a4330"), width=10, joint="curve")
    for (x, y) in pts[1:]:
        flower(d, x, y, rnd.randint(34, 48), petal, edge=deep, rot=rnd.randint(0, 70))
    for _ in range(8):
        t = rnd.random()
        x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * (0.75 - 0.55 * t) + rnd.randint(-70, 70)
        flower(d, x, y, rnd.randint(18, 28), petal, edge=deep, rot=rnd.randint(0, 70))


def peony(d, box, base, petal="#f2a6bd", deep="#c0587a", seed=3):
    """모란 수자수: 겹겹의 큰 꽃송이와 잎."""
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(base))
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    for dx, dy in ((-170, 90), (190, 120), (20, -190)):
        for leaf in range(4):
            a = math.radians(leaf * 90 + 25)
            d.ellipse((cx + dx + 70 * math.cos(a) - 40, cy + dy + 70 * math.sin(a) - 22,
                       cx + dx + 70 * math.cos(a) + 40, cy + dy + 70 * math.sin(a) + 22), fill=rgb("#6fa56a"))
    for dx, dy, r in ((0, 0, 120), (-190, 100, 80), (200, 130, 70)):
        for ring, shade in enumerate((deep, petal, "#f8d3df", "#fff0f4")):
            rr = r * (1 - ring * 0.22)
            for k in range(8):
                a = math.radians(k * 45 + ring * 20)
                d.ellipse((cx + dx + rr * 0.5 * math.cos(a) - rr * 0.5, cy + dy + rr * 0.5 * math.sin(a) - rr * 0.5,
                           cx + dx + rr * 0.5 * math.cos(a) + rr * 0.5, cy + dy + rr * 0.5 * math.sin(a) + rr * 0.5),
                          fill=rgb(shade), outline=rgb(deep, 120), width=2)
        d.ellipse((cx + dx - 14, cy + dy - 14, cx + dx + 14, cy + dy + 14), fill=rgb("#f3c24f"))


def shell(d, box, seed=4):
    """나전: 어두운 옻칠 바탕에 자개 조각이 무지갯빛으로 반짝이는 모양."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb("#14232b"))
    tones = ["#8fd4d0", "#b8e6e0", "#c9b6e8", "#f0c6d8", "#9ec7f0", "#d8f0c8"]
    for _ in range(int((x1 - x0) * (y1 - y0) / 650)):
        x, y = rnd.randint(int(x0), int(x1)), rnd.randint(int(y0), int(y1))
        w, h = rnd.randint(14, 34), rnd.randint(10, 26)
        color = rnd.choice(tones)
        d.polygon([(x, y), (x + w, y + h // 3), (x + w - 6, y + h), (x - 4, y + h - 6)], fill=rgb(color, rnd.randint(150, 235)),
                  outline=rgb("#ffffff", 90))
    for i in range(0, int(x1 - x0), 120):  # 줄 상감
        d.line((x0 + i, y0, x0 + i + 60, y1), fill=rgb("#e8f5f3", 70), width=3)


def knot(d, box, light, dark, seed=5):
    """매듭: 고리가 엇갈려 엮인 끈."""
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(light))
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    for ring in range(3):
        r = 250 - ring * 65
        for k in range(8):
            a = math.radians(k * 45 + ring * 22)
            ox, oy = cx + r * 0.55 * math.cos(a), cy + r * 0.55 * math.sin(a)
            d.ellipse((ox - r * 0.42, oy - r * 0.42, ox + r * 0.42, oy + r * 0.42), outline=rgb(dark, 230 - ring * 40), width=14 - ring * 3)
    d.ellipse((cx - 30, cy - 30, cx + 30, cy + 30), fill=rgb(dark))


def enamel(d, box, base, cells, seed=6):
    """칠보: 금속 선으로 칸을 나누고 유약을 채운 모양."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb("#d9b44a"))
    step = 110
    for y in range(int(y0), int(y1), step):
        for x in range(int(x0), int(x1), step):
            inset = 12
            d.ellipse((x + inset, y + inset, x + step - inset, y + step - inset), fill=rgb(rnd.choice(cells)),
                      outline=rgb("#8a6a1f"), width=4)
            d.ellipse((x + 38, y + 38, x + step - 38, y + step - 38), fill=rgb(base), outline=rgb("#8a6a1f"), width=3)


def hanji(d, box, light, dark, seed=7):
    """한지: 닥나무 섬유가 불규칙하게 엉킨 종이 결."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(light))
    for _ in range(int((x1 - x0) * (y1 - y0) / 220)):
        x, y = rnd.randint(int(x0), int(x1)), rnd.randint(int(y0), int(y1))
        a = rnd.random() * math.pi
        l = rnd.randint(16, 60)
        d.line((x, y, x + l * math.cos(a), y + l * math.sin(a)), fill=rgb(dark, rnd.randint(40, 110)), width=1)


def silver(d, box, base, line, seed=8):
    """은·금 세공: 가는 새김 선과 반짝이는 결."""
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(base))
    for k in range(-10, 30):
        d.line((x0 + k * 40, y0, x0 + k * 40 + 160, y1), fill=rgb("#ffffff", 110), width=10)
    for ring in range(1, 9):
        r = ring * 48
        d.ellipse(((x0 + x1) / 2 - r, (y0 + y1) / 2 - r, (x0 + x1) / 2 + r, (y0 + y1) / 2 + r), outline=rgb(line, 150), width=3)
    flower(d, (x0 + x1) / 2, (y0 + y1) / 2, 46, "#ffffff", center="#d9b44a", edge=line)


def jade(d, box, base, seed=9):
    """옥: 푸른 초록빛이 안개처럼 번진 돌 무늬."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(base))
    for _ in range(26):
        x, y = rnd.randint(int(x0), int(x1)), rnd.randint(int(y0), int(y1))
        r = rnd.randint(40, 140)
        d.ellipse((x - r, y - r // 2, x + r, y + r // 2), fill=rgb(rnd.choice(["#ffffff", "#2f7a5c", "#a8dcc4"]), 38))


def pearls(d, box, light, seed=10):
    """진주: 은은한 광택의 둥근 알이 줄지어 있는 모양."""
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(light))
    for row in range(5):
        y = y0 + 90 + row * 130
        for col in range(int((x1 - x0) // 84) + 1):
            x = x0 + 40 + col * 84 + (42 if row % 2 else 0)
            d.ellipse((x - 38, y - 38, x + 38, y + 38), fill=rgb("#f4efe8"), outline=rgb("#cfc4b4"), width=3)
            d.ellipse((x - 22, y - 26, x - 6, y - 10), fill=rgb("#ffffff", 220))


def ink(d, box, light, dark, seed=11):
    """먹: 붓이 지나간 굵고 가는 획."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(light))
    for _ in range(7):
        sx, sy = rnd.randint(int(x0) + 40, int(x1) - 200), rnd.randint(int(y0) + 60, int(y1) - 60)
        pts = [(sx + t * 40, sy + 70 * math.sin(t * 0.8 + rnd.random())) for t in range(12)]
        d.line(pts, fill=rgb(dark, rnd.randint(150, 235)), width=rnd.randint(8, 30), joint="curve")


def landscape(d, box, light, dark, seed=12):
    """산수화: 먹빛 산과 물."""
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(light))
    for k, (cx, h, shade) in enumerate(((0.25, 330, 90), (0.55, 440, 140), (0.85, 300, 100))):
        cx = x0 + (x1 - x0) * cx
        d.polygon([(cx - 280, y1 - 150), (cx, y1 - 150 - h), (cx + 280, y1 - 150)], fill=rgb(dark, shade))
    d.rectangle((x0, y1 - 150, x1, y1), fill=rgb("#dfeadf"))
    for y in range(int(y1) - 120, int(y1), 26):
        d.line((x0 + 30, y, x1 - 30, y), fill=rgb(dark, 70), width=3)
    d.ellipse((x1 - 190, y0 + 60, x1 - 90, y0 + 160), fill=rgb("#c0392b", 190))


def crackle(d, box, base, seed=13):
    """청자: 푸른 유약의 빙렬과 학·구름 상감."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(base))
    for _ in range(90):
        x, y = rnd.randint(int(x0), int(x1)), rnd.randint(int(y0), int(y1))
        pts = [(x, y)]
        for _ in range(4):
            x += rnd.randint(-50, 50)
            y += rnd.randint(-50, 50)
            pts.append((x, y))
        d.line(pts, fill=rgb("#4d7f6e", 70), width=2)
    for cx, cy in ((250, 260), (560, 520)):
        d.ellipse((cx - 70, cy - 28, cx + 70, cy + 28), fill=rgb("#f7f4ea"))
        d.line([(cx + 55, cy - 12), (cx + 120, cy - 90), (cx + 145, cy - 84)], fill=rgb("#f7f4ea"), width=14)
        d.polygon([(cx - 40, cy), (cx - 160, cy - 80), (cx + 10, cy - 20)], fill=rgb("#f7f4ea"))
    for cx, cy in ((450, 170), (200, 560)):
        for dx, r in ((-50, 30), (0, 44), (50, 30)):
            d.ellipse((cx + dx - r, cy - r, cx + dx + r, cy + r), fill=rgb("#f7f4ea"))


def gwiyal(d, box, base, seed=14):
    """분청 귀얄: 백토를 풀비로 쓸듯이 바른 결과 철화 선."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgb(base))
    for y in range(int(y0) + 20, int(y1), 46):
        d.line((x0 + rnd.randint(0, 40), y, x1 - rnd.randint(0, 40), y + rnd.randint(-8, 8)), fill=rgb("#f7f2e6", 235), width=26)
        for _ in range(6):
            yy = y + rnd.randint(-10, 10)
            d.line((x0 + 30, yy, x1 - 30, yy + rnd.randint(-4, 4)), fill=rgb("#cbbfa4", 120), width=2)
    for x in range(int(x0) + 60, int(x1), 150):
        d.line([(x, y1 - 40), (x + 14, y1 - 150), (x + 40, y1 - 210)], fill=rgb("#3a2c22"), width=8, joint="curve")


def paint(texture, d, box, palette, extras=None):
    """texture 이름으로 알맞은 무늬를 그린다. palette = (위, 아래, 포인트, 진한 색, 밝은 색)."""
    top, bottom, accent, dark, light = palette
    extras = extras or {}
    if texture == "weave":
        weave(d, box, light, dark)
    elif texture == "quilt":
        quilt(d, box, light, accent)
    elif texture == "patchwork":
        patchwork(d, box, extras.get("colors") or [accent, light, dark, bottom, top])
    elif texture == "plum":
        plum(d, box, light)
    elif texture == "peony":
        peony(d, box, light)
    elif texture == "shell":
        shell(d, box)
    elif texture == "knot":
        knot(d, box, light, accent)
    elif texture == "enamel":
        enamel(d, box, light, [accent, dark, "#2f8f8a", "#c0392b"])
    elif texture == "hanji":
        hanji(d, box, light, dark)
    elif texture == "silver":
        silver(d, box, light, dark)
    elif texture == "jade":
        jade(d, box, accent)
    elif texture == "pearls":
        pearls(d, box, light)
    elif texture == "ink":
        ink(d, box, "#f3efe4", "#141414")
    elif texture == "landscape":
        landscape(d, box, light, dark)
    elif texture == "crackle":
        crackle(d, box, accent)
    elif texture == "gwiyal":
        gwiyal(d, box, "#d8cdb5")
    else:
        raise ValueError(texture)
