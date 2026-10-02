#!/usr/bin/env python3
"""시연 디자인(홈 베스트 5·신상품 4·기획전 4)에 나오는 상품 13개를 DB 에 심는 V23 마이그레이션을 만든다.

노출 순서는 FE 가 실제 API 로 부를 때를 가정한다.
  베스트  = sort=POPULAR(id 내림차순) 1~5번,  기획전 = POPULAR 6~9번,  신상품 = sort=NEWEST 4개
  → 베스트 1번이 가장 큰 id, 기획전이 그다음, 신상품 4개는 등록 시각이 가장 최근이다.
이미지: 디자인 원본이 있는 5개는 docs/demo-products/design/, 없는 것은 우리 실사 이미지(photoreal/staging)에서 비슷한 것을 쓴다.
실행: python3 scripts/demo-products/seed_design_products.py
"""
import os

HERE = os.path.dirname(__file__)
ROOT = os.path.join(HERE, "..", "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "db", "migration", "V23__demo_design_products.sql")
RAW = "https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-products"
ST, DS = f"{RAW}/photoreal/staging", f"{RAW}/design"

# (그룹, 순번, 이름, 장인 ID, 소분류, 가격, 소재, 이미지, 색상, 설명)
ITEMS = [
    ("best", 1, "청자 분청 찻잔", 20, 1, 120000, "청자", f"{ST}/d007_A.webp", ["연청자"],
     "연한 비색 유약을 입혀 구운 찻잔입니다. 손에 쥐면 가볍고, 따뜻한 차를 따르면 유약 속 빛이 은은하게 올라옵니다."),
    ("best", 2, "옻칠 원형 쟁반", 18, 5, 95000, "원목 옻칠", f"{ST}/d076_A.webp", ["갈색", "흑색"],
     "나무를 둥글게 깎아 옻칠을 여러 번 덧발라 말린 쟁반입니다. 찻잔과 다과를 올리면 붉은 윤기가 상을 단정하게 받쳐 줍니다."),
    ("best", 3, "옥 매듭 반지", 34, 17, 400000, "천연 옥", f"{ST}/d219_A.webp", ["연옥색"],
     "결 고운 옥을 갈고 닦아 매듭 모양을 새긴 반지입니다. 착용할수록 옥의 윤기가 깊어집니다."),
    ("best", 4, "전통 한지 무드등", 25, 10, 68000, "한지 원목", f"{ST}/p15_A.webp", ["호박색"],
     "손으로 뜬 한지를 원통형 살대에 바른 스탠드입니다. 불을 켜면 한지 결 사이로 따뜻한 빛이 퍼집니다."),
    ("best", 5, "왕골 원형 부채", 37, 25, 22000, "왕골", f"{ST}/p31_A.webp", ["살구색", "황토색"],
     "왕골을 한 올씩 엮어 둥글게 짠 부채입니다. 바람이 부드럽게 일고 여름 내내 곁에 두기 좋습니다."),
    ("new", 1, "청사초롱 벽등", 25, 10, 95000, "한지 원목", f"{DS}/new-cheongsa-lantern.webp", ["홍색", "청색"],
     "홍색과 청색 한지를 두른 청사초롱 모양의 벽등입니다. 불을 켜면 격자 문양 사이로 붉고 푸른 빛이 번집니다."),
    ("new", 2, "전통 매화 은반지", 34, 17, 56000, "순은", f"{DS}/new-plum-silver-ring.webp", ["은색"],
     "은을 두드려 매화 가지와 꽃을 한 땀씩 새긴 반지입니다. 쓸수록 은 특유의 부드러운 빛이 깊어집니다."),
    ("new", 3, "모시 여름 접선", 32, 25, 28000, "한산모시 대나무", f"{ST}/f25_A.webp", ["백색", "미색", "연하늘색"],
     "대나무 뼈대에 모시를 입혀 접어 쓰는 여름 부채입니다. 결이 고와 바람이 가볍게 일고 보관도 간편합니다."),
    ("new", 4, "옻칠 흑선", 41, 25, 120000, "한지 대나무 옻칠", f"{ST}/f25_C.webp", ["흑색"],
     "한지에 옻칠을 입혀 검게 마감한 접부채입니다. 펼치면 깊은 윤기가 돌고 접으면 단정한 선이 남습니다."),
    ("plan", 1, "대나무 조명", 22, 10, 500000, "대나무", f"{ST}/f10_A.webp", ["황토색"],
     "가는 대나무를 하나씩 엮어 만든 조명입니다. 불을 켜면 엮은 결을 따라 따뜻한 그림자가 번집니다."),
    ("plan", 2, "백잔", 13, 1, 100000, "백자", f"{ST}/d008_A.webp", ["백색"],
     "맑은 백토로 빚어 투명한 유약을 입힌 작은 잔입니다. 차의 빛깔이 그대로 비쳐 보입니다."),
    ("plan", 3, "자연염 테이블 러너", 32, 36, 140000, "한산모시", f"{DS}/plan-ramie-table-runner.webp", ["회갈색", "분홍", "녹색"],
     "자연에서 얻은 색으로 물들인 모시 러너입니다. 상차림 가운데 길게 깔면 은은한 결이 식탁을 단정하게 만듭니다."),
    ("plan", 4, "산수화 대형 부채", 41, 25, 85000, "한지 대나무", f"{DS}/plan-landscape-large-fan.webp", ["미색", "백색"],
     "한지 위에 먹과 담채로 산수를 그린 대형 접부채입니다. 펼쳐 두면 그대로 한 폭의 그림이 됩니다."),
]


def q(s):
    return "'" + s.replace("'", "''") + "'"


def main():
    # id 오름차순: 신상품(4→1) → 기획전(4→1) → 베스트(5→1)  ⇒ POPULAR(id 내림차순)에서 베스트 1번이 맨 앞
    order = [i for g in ("new", "plan", "best") for i in sorted([x for x in ITEMS if x[0] == g], key=lambda x: -x[1])]
    rows = []
    for ord_, (group, no, title, artisan, sub, price, material, image, colors, desc) in enumerate(order, 1):
        # 신상품 4개는 가장 최근 등록(1번이 최신), 나머지는 하루 전
        created = f"NOW() - ({no} * INTERVAL '1 second')" if group == "new" else "NOW() - INTERVAL '1 day'"
        rows.append(f"    ({ord_}, {q(title)}, {artisan}, {sub}, {q(desc)}, {q(material)}, {price}, 30, {q(image)}, 14, {created})")
    colors = ",\n".join(f"    ({q(it[2])}, {it[3]}, {q(c)})" for it in ITEMS for c in it[8])
    rows_sql = ",\n".join(rows)
    text = f"""-- 시연 디자인(홈 베스트 5·기획전 4·신상품 4)의 상품 13개를 심는다. 원본: scripts/demo-products/seed_design_products.py
-- POPULAR(id 내림차순)에서 베스트 1~5번·기획전 6~9번, NEWEST 상위 4개가 신상품이 되도록 id 와 등록 시각을 정했다.
-- 디자인 원본 이미지가 있는 5개는 docs/demo-products/design/, 나머지는 비슷한 우리 실사 이미지를 쓴다.

INSERT INTO product (artisan_id, category_id, subcategory_id, title, description, material, price, stock,
                     thumbnail_url, production_period_days, is_limited, is_custom_order, is_single_item,
                     has_gift_wrap, status, created_at, updated_at)
SELECT v.artisan_id, s.category_id, v.subcategory_id, v.title, v.description, v.material, v.price, v.stock,
       v.thumbnail_url, v.days, FALSE, FALSE, FALSE, FALSE, 'ON_SALE', v.created_at, NOW()
FROM (VALUES
{rows_sql}
) AS v(ord, title, artisan_id, subcategory_id, description, material, price, stock, thumbnail_url, days, created_at)
JOIN subcategory s ON s.subcategory_id = v.subcategory_id
WHERE NOT EXISTS (SELECT 1 FROM product e WHERE e.title = v.title AND e.artisan_id = v.artisan_id)
ORDER BY v.ord;

INSERT INTO product_color (product_id, color)
SELECT p.product_id, c.color
FROM (VALUES
{colors}
) AS c(title, artisan_id, color)
JOIN product p ON p.title = c.title AND p.artisan_id = c.artisan_id
ON CONFLICT DO NOTHING;
"""
    open(OUT, "w", encoding="utf-8").write(text)
    print(len(ITEMS), "products ->", OUT)


if __name__ == "__main__":
    main()
