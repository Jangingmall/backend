# 시연용 실사 이미지 제작 브리프 (Gemini 의뢰용)

홈 화면과 시연 영상에 쓸 **상위 20개 상품**의 실사풍 이미지를 이미지 생성 AI(Gemini)로 만들기 위한 브리프다. 받은 이미지를 BE 쪽에서 WebP 로 변환해 상품 이미지로 연결한다(새 마이그레이션 V20).

## 1. 필요한 이미지 수

| 단계 | 구성 | 장수 | 설명 |
|---|---|---|---|
| 최소 | A(대표) 20 | 20장 | 홈·목록 카드에 쓰는 대표 이미지만 |
| 권장 | A + B(소재 확대) | 40장 | 상세 화면 2번째 이미지까지 실사로 |
| 전체 | A + B + C(장면) | 60장 | 상세 갤러리 3장 모두 실사 |

권장은 **40장**이다. 시연 영상에 가장 오래 비치는 건 홈 카드(A)와 상세 첫 화면(A·B)이다.

## 2. 대상 상품 20개 (선정 기준)

홈의 신상품·선물 첫 화면은 최신 등록순이고, V17 이 합죽선·청자·분청·노리개·수자수·조명·모시 스카프·나전 명함집 순으로 먼저 보이게 해 두었다. 그 뒤로 p01~p14 를 이어 20개를 골랐다.

| 키 | 상품명 | 소재 | 색 |
|---|---|---|---|
| p31 | 전주 합죽선 · 매화선 | 대나무·한지 | 흰색 |
| p32 | 청자 운학문 찻잔 | 청자 | 비색 |
| p33 | 분청 귀얄 찻잔 | 분청사기 | 회백색 |
| p06 | 홍매 삼작 노리개 | 명주실 | 홍색 |
| p11 | 수자수 모란도 액자 | 명주 수자수 | 분홍 |
| p15 | 한지 무드 조명 | 한지·원목 | 호박색 |
| p16 | 하늘빛 한산모시 스카프 | 한산모시 | 하늘색 |
| p26 | 나전 명함집 | 나전·옻칠 | 청록 |
| p01 | 연분홍 모시 생활한복 저고리 | 한산모시 | 연분홍 |
| p02 | 쪽빛 누비 복주머니 | 면 누비 | 쪽빛 |
| p03 | 매화 자수 손수건 세트 | 무명 | 자주 |
| p04 | 오방색 조각보 파우치 | 명주 | 오방색 |
| p05 | 연둣빛 모시 스카프 | 모시 | 연두 |
| p07 | 쪽빛 매듭 키링 | 명주실 | 쪽빛 |
| p08 | 조각보 카드지갑 | 명주 | 청록 |
| p09 | 은빛 매화 비녀 | 은 | 은색 |
| p10 | 옥빛 매듭 귀걸이 | 옥·명주실 | 옥색 |
| p12 | 한지 산수화 액자 | 한지 | 녹색 |
| p13 | 오방 누비 방석 | 명주 누비 | 황토 |
| p14 | 모시 테이블 러너 | 모시 | 아이보리 |

## 3. 파일 규칙 (ZIP)

Gemini 웹앱은 이미지를 ZIP 으로 묶어 주지 못할 수 있다. 이미지를 내려받은 뒤 **아래 이름으로 바꿔서 직접 압축**한다.

```
photoreal.zip
├─ p31_A.png   p31_B.png   p31_C.png     # A=대표  B=소재 확대  C=장면(선택)
├─ p32_A.png   p32_B.png   ...
└─ manifest.csv                           # 선택: key,파일명,사용한 프롬프트 번호
```

- 형식은 PNG 또는 JPG, **정사각형(1:1), 짧은 변 1600px 이상**(2048 권장). WebP 변환은 BE 에서 한다.
- 파일 하나는 10MB 이하. 한 이미지에는 상품 한 점(세트는 한 구도)만 나오게 한다.
- 키(`p31` …)와 A/B/C 를 틀리지 않게 쓴다. 이 이름으로 자동 연결한다.

### 3-1. 이미지 ↔ 상품 ID 매칭

- **파일명 규칙**이 1차 매칭이다: `<key>_<컷>.<확장자>` (예: `p31_A.png` = 전주 합죽선 대표).
- **JSON 매칭표**: `docs/demo-products/photoreal/manifest.template.json` 에 20개 상품의 `key`·`match`(상품명+장인 ID, V17 의 자연키)·기대 파일명이 있다.
  `dbProductId` 는 DB 마다 달라서(로컬·STG 다름) 비워 두고, 적용 시 `match`(title+artisanId)로 상품을 찾아 채운다.
- **검증 + 매칭표 생성**: ZIP 을 받으면 아래 한 줄로 이름·해상도·정사각형·용량을 확인하고, 실제 파일 정보(크기·sha256)가 든 `manifest.json` 을 만든다.

```
python3 scripts/demo-products/photoreal_check.py photoreal.zip
```

  대표(A)가 빠졌거나 규칙을 어긴 파일이 있으면 오류로 알려 준다(종료 코드 1).

## 4. Gemini 에 붙여 넣을 안내문 (그대로 복사)

```text
너는 전통 공예 쇼핑몰 '미담'의 상품 사진 디렉터야. 아래 상품들의 실사풍 제품 사진을 한 장씩 만들어 줘.

공통 규칙
- Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
- 배경은 따뜻한 밝은 회색(또는 아주 옅은 베이지) 무봉제 스튜디오 배경, 제품 아래에 부드러운 자연스러운 그림자.
- 제품은 화면 중앙, 여백은 사방 12~15%. 상품 하나가 한눈에 보이게.
- 한국 전통 공예품의 소재감(모시, 명주, 옻칠, 나전, 청자, 한지 등)이 정확히 느껴져야 해. 색은 내가 적은 색을 따라줘.
- Do NOT add any text, captions, price tags, logos, watermarks, borders or collage layouts. One single product photo per image.
- 한 번에 이미지 1장만 만들어 줘. 마음에 안 들면 같은 설명으로 다시 만들어 줘(구도·조명을 비슷하게 유지).
- 모든 상품의 조명 방향(왼쪽 위 45도)과 배경 색을 똑같이 유지해서 한 쇼핑몰 사진처럼 통일해 줘.

컷 종류
- A(대표): 제품 전체가 보이는 정면 3/4 시점 패킹샷.
- B(소재 확대): 같은 제품의 소재·무늬 클로즈업(매크로).
- C(장면): 같은 제품을 자연스러운 생활 장면에 놓은 사진(사람 없이).

이제 내가 '상품 하나 + 컷 종류'를 보내면 그 사진을 만들어 줘.
```

## 5. 상품별 프롬프트

아래 문장을 **그대로** Gemini 에 보낸다. 공통 규칙은 4번 안내문에 이미 있으므로 반복하지 않아도 되지만, 안전하게 끝줄에 붙어 있다.

### p31 · 전주 합죽선 · 매화선

- 소재 대나무·한지 / 색 흰색 / 특징: 대나무 겉대 두 쪽을 붙인 합죽선 · 닥나무 한지 선면 · 선추 달린 접부채

**A (대표) → 저장 파일명 `p31_A.png`**
```text
Create image A of a Korean traditional folding fan (hapjukseon) fully opened in a half-circle, two-layer bamboo ribs with warm natural tone, white mulberry-paper (hanji) fan face painted with delicate plum blossom branches in ink and pale pink, a small knotted tassel pendant at the handle. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p31_B.png`**
```text
Create image B: extreme close-up of the paper fan face with hand-painted plum blossoms and the fine bamboo ribs, visible mulberry paper fibers. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p31_C.png`**
```text
Create image C: the opened fan resting on a dark wooden tray beside a small celadon tea cup, soft window light. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p32 · 청자 운학문 찻잔

- 소재 청자 / 색 비색 / 특징: 비색 유약 · 운학문 상감 · 용량 약 150ml

**A (대표) → 저장 파일명 `p32_A.png`**
```text
Create image A of a Korean celadon tea cup with luminous translucent jade-green (bisaek) glaze, inlaid crane-and-cloud (unhak) pattern in white and black slip, about 150ml, small foot ring. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p32_B.png`**
```text
Create image B: close-up of the inlaid cranes and clouds on the glaze, showing fine craquelure and glaze pooling. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p32_C.png`**
```text
Create image C: the cup on a small wooden tea tray with steam from green tea, a ceramic teapot softly blurred behind. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p33 · 분청 귀얄 찻잔

- 소재 분청사기 / 색 회백색 / 특징: 백토 귀얄 분장 · 철화 풀꽃 무늬 · 용량 약 150ml

**A (대표) → 저장 파일명 `p33_A.png`**
```text
Create image A of a Korean buncheong stoneware tea cup, grayish-white slip applied with bold brush strokes (gwiyal), simple iron-brown wildflower pattern, about 150ml, rustic and warm. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p33_B.png`**
```text
Create image B: close-up of the brush-stroke slip texture and the hand-painted iron-brown grass flowers. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p33_C.png`**
```text
Create image C: the cup on a linen cloth next to a small tea caddy, natural daylight. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p06 · 홍매 삼작 노리개

- 소재 명주실 / 색 홍색 / 특징: 삼작 구성 · 손매듭 끈 · 한복 고름에 걸어 사용

**A (대표) → 저장 파일명 `p06_A.png`**
```text
Create image A of a Korean traditional ornament (norigae) in the three-piece (samjak) style, hand-knotted crimson-red silk cord knots with a plum-blossom shaped main knot and long tassels, hanging from a small hook. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p06_B.png`**
```text
Create image B: close-up of the intricate hand-tied silk knots and tassel fibers, deep red silk with soft sheen. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p06_C.png`**
```text
Create image C: the norigae hanging against a pale pink hanbok ribbon (goreum), soft background. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p11 · 수자수 모란도 액자

- 소재 명주 수자수 / 색 분홍 / 특징: 손수자수 · 원목 액자 표구 · 가로 40cm × 세로 50cm

**A (대표) → 저장 파일명 `p11_A.png`**
```text
Create image A of a framed Korean silk satin-stitch embroidery of blooming pink peonies with green leaves on ivory silk, plain natural wood frame, portrait orientation 40x50 cm proportion. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p11_B.png`**
```text
Create image B: close-up of the satin-stitch embroidery showing silk thread sheen and petal shading. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p11_C.png`**
```text
Create image C: the framed embroidery on a wall above a small wooden console in a calm minimalist room. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p15 · 한지 무드 조명

- 소재 한지·원목 / 색 호박색 / 특징: 한지 갓 · 원목 받침 · 전구 색온도 2700K

**A (대표) → 저장 파일명 `p15_A.png`**
```text
Create image A of a Korean hanji paper mood lamp with a softly glowing warm amber paper shade (visible paper fibers) on a simple solid wood base, 2700K warm light. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p15_B.png`**
```text
Create image B: close-up of the lit hanji paper shade showing fibers and warm light diffusion. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p15_C.png`**
```text
Create image C: the lit lamp on a bedside table in a dim, cozy room. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p16 · 하늘빛 한산모시 스카프

- 소재 한산모시 / 색 하늘색 / 특징: 천연 염색 · 한산모시 100% · 가로 40cm × 세로 160cm

**A (대표) → 저장 파일명 `p16_A.png`**
```text
Create image A of a sky-blue naturally dyed Hansan ramie (mosi) scarf, thin airy gauze weave, softly draped and loosely folded, small fringe at both ends. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p16_B.png`**
```text
Create image B: extreme close-up of the open ramie weave texture and the light blue natural dye. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p16_C.png`**
```text
Create image C: the scarf draped over a wooden chair back in soft morning light. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p26 · 나전 명함집

- 소재 나전·옻칠 / 색 청록 / 특징: 나전 장식 · 옻칠 마감 · 명함 30장 수납

**A (대표) → 저장 파일명 `p26_A.png`**
```text
Create image A of a slim Korean business card case with black urushi lacquer finish and an inlaid turquoise-green mother-of-pearl (najeon) floral pattern on the lid, slightly open showing blank cards. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p26_B.png`**
```text
Create image B: close-up of the mother-of-pearl inlay with iridescent shimmer over glossy black lacquer. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p26_C.png`**
```text
Create image C: the case on a dark desk next to a fountain pen, reflective surface. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p01 · 연분홍 모시 생활한복 저고리

- 소재 한산모시 / 색 연분홍 / 특징: 한산모시 100% · 손바느질 마감 · 품 넉넉한 생활한복 패턴

**A (대표) → 저장 파일명 `p01_A.png`**
```text
Create image A of a pale pink Korean daily hanbok jeogori (jacket) made of ramie, flat lay neatly arranged, hand-stitched seams, relaxed fit. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p01_B.png`**
```text
Create image B: close-up of the ramie fabric weave and the hand-stitched collar seam in pale pink. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p01_C.png`**
```text
Create image C: the jeogori hanging on a wooden hanger against a white wall, soft light. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p02 · 쪽빛 누비 복주머니

- 소재 면 누비 / 색 쪽빛 / 특징: 손누비 마감 · 매듭 끈 장식 · 손바닥 크기

**A (대표) → 저장 파일명 `p02_A.png`**
```text
Create image A of an indigo-blue quilted cotton Korean lucky pouch (bokjumeoni) with hand diamond quilting and a knotted cord closure, palm-sized. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p02_B.png`**
```text
Create image B: close-up of the hand diamond quilting stitches on deep indigo cotton and the knotted cord. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p02_C.png`**
```text
Create image C: the pouch resting on a folded linen cloth with a small sprig of dried flowers. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p03 · 매화 자수 손수건 세트

- 소재 무명 / 색 자주 / 특징: 손자수 매화 · 부드러운 무명 · 2장 1세트

**A (대표) → 저장 파일명 `p03_A.png`**
```text
Create image A of a set of two aubergine-purple cotton handkerchiefs, neatly folded with a hand-embroidered plum blossom branch in the corner. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p03_B.png`**
```text
Create image B: close-up of the embroidered plum blossoms on soft purple cotton. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p03_C.png`**
```text
Create image C: the two handkerchiefs stacked on a wooden tray, tied with a thin cord. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p04 · 오방색 조각보 파우치

- 소재 명주 / 색 오방색 / 특징: 조각보 기법 · 안감 포함 · 지퍼 마감

**A (대표) → 저장 파일명 `p04_A.png`**
```text
Create image A of a Korean bojagi-style patchwork zipper pouch in silk with five traditional colors (blue, yellow, red, white, black) in geometric patches. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p04_B.png`**
```text
Create image B: close-up of the patchwork seams and the five traditional colors of silk. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p04_C.png`**
```text
Create image C: the pouch on a pale wooden table beside a folded bojagi cloth. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p05 · 연둣빛 모시 스카프

- 소재 모시 / 색 연두 / 특징: 통풍이 좋은 모시 · 가벼운 무게 · 끝단 술 장식

**A (대표) → 저장 파일명 `p05_A.png`**
```text
Create image A of a light yellow-green ramie scarf with a delicate open weave and small fringe, softly folded. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p05_B.png`**
```text
Create image B: close-up of the open ramie weave in fresh light green and the fringe ends. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p05_C.png`**
```text
Create image C: the scarf loosely tied on a rattan bag handle in daylight. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p07 · 쪽빛 매듭 키링

- 소재 명주실 / 색 쪽빛 / 특징: 손매듭 · 가벼운 무게 · 작은 선물로 적합

**A (대표) → 저장 파일명 `p07_A.png`**
```text
Create image A of an indigo-blue hand-knotted Korean maedeup keyring with a decorative knot and short tassel on a metal ring. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p07_B.png`**
```text
Create image B: close-up of the intricate knot structure in indigo silk cord. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p07_C.png`**
```text
Create image C: the keyring lying next to a small wrapped gift box. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p08 · 조각보 카드지갑

- 소재 명주 / 색 청록 / 특징: 카드 6칸 · 조각보 무늬 · 얇은 두께

**A (대표) → 저장 파일명 `p08_A.png`**
```text
Create image A of a slim card wallet made of teal and blue-green silk patchwork (jogakbo) pieces, closed, thin profile. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p08_B.png`**
```text
Create image B: close-up of the patchwork silk pieces in teal tones and neat edge stitching. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p08_C.png`**
```text
Create image C: the wallet open slightly with blank cards on a light wooden surface. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p09 · 은빛 매화 비녀

- 소재 은 / 색 은색 / 특징: 순은 도금 · 매화 문양 조각 · 길이 약 14cm

**A (대표) → 저장 파일명 `p09_A.png`**
```text
Create image A of a slender silver Korean hairpin (binyeo), about 14 cm long, with a carved plum blossom ornament at the head, polished silver. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p09_B.png`**
```text
Create image B: close-up of the carved plum blossom head and the fine engraving, with soft specular highlights. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p09_C.png`**
```text
Create image C: the hairpin placed on a small dark velvet cushion. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p10 · 옥빛 매듭 귀걸이

- 소재 옥·명주실 / 색 옥색 / 특징: 옥빛 구슬 · 손매듭 장식 · 알레르기 줄인 은침

**A (대표) → 저장 파일명 `p10_A.png`**
```text
Create image A of a pair of earrings with jade-green beads and small hand-tied silk knots with silver posts, elegant and minimal. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p10_B.png`**
```text
Create image B: close-up of the jade-green beads and the delicate knot detail. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p10_C.png`**
```text
Create image C: the earrings on a small ceramic dish with a soft pink flower petal. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p12 · 한지 산수화 액자

- 소재 한지 / 색 녹색 / 특징: 수제 한지 · 먹·채색 산수화 · 원목 액자

**A (대표) → 저장 파일명 `p12_A.png`**
```text
Create image A of a framed Korean hanji landscape painting in ink and light green color with layered mountains, natural wood frame, handmade paper with visible fibers. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p12_B.png`**
```text
Create image B: close-up of the ink brush strokes and mineral green color on hanji paper. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p12_C.png`**
```text
Create image C: the framed painting leaning on a shelf with a small ceramic vase. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p13 · 오방 누비 방석

- 소재 명주 누비 / 색 황토 / 특징: 손누비 · 솜 충전 두께 5cm · 가로·세로 45cm

**A (대표) → 저장 파일명 `p13_A.png`**
```text
Create image A of a square quilted silk cushion (45 cm) in warm ochre-brown with hand diamond quilting stitches, plump 5cm thickness. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p13_B.png`**
```text
Create image B: close-up of the diamond quilting and the silk sheen in ochre. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p13_C.png`**
```text
Create image C: the cushion placed on a Korean wooden floor seating area, warm light. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

### p14 · 모시 테이블 러너

- 소재 모시 / 색 아이보리 / 특징: 모시 100% · 가장자리 술 장식 · 길이 150cm

**A (대표) → 저장 파일명 `p14_A.png`**
```text
Create image A of an ivory ramie table runner 150 cm long with fringed ends, laid on a light wooden dining table. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**B (소재 확대) → 저장 파일명 `p14_B.png`**
```text
Create image B: close-up of the ivory ramie weave and the fringe edge. Same product as image A, macro detail shot, shallow depth of field. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```
**C (장면) → 저장 파일명 `p14_C.png`**
```text
Create image C: the runner on a set table with two white ceramic cups and a small flower vase. Same product as image A, natural lifestyle scene without people, realistic. Professional e-commerce product photography, photorealistic, shot on a full-frame camera with an 85mm lens, soft diffused studio lighting, accurate natural colors and true material textures, sharp focus, 1:1 square, ultra high resolution. No text, no letters, no logo, no watermark, no brand names, no people, no hands.
```

## 6. 받은 뒤 확인할 것 (체크리스트)

- [ ] 상품 모양·색이 이름·설명과 맞는가 (예: 청자는 비색 유약, 조각보 파우치는 오방색)
- [ ] 글자·로고·워터마크·사람·손이 없는가
- [ ] 한 이미지에 상품이 한 점(세트는 한 구도)만 있는가, 상품이 잘리지 않았는가
- [ ] 20개 모두 배경·조명 톤이 비슷한가
- [ ] 전통 공예품이 어색하게 변형되지 않았는가(손가락 수, 부채살 수, 매듭 구조, 한복 여밈 방향 등)

## 7. 시연 시 안내 문구 (정직성)

이 이미지는 **AI 로 생성한 실사풍 이미지**다. 시연·영상에서 '실제 장인이 촬영한 사진'이라고 말하지 않는다. 예: "시연용으로 AI 가 만든 상품 이미지입니다."

## 8. 받은 뒤 BE 처리 (참고)

0. `photoreal_check.py` 로 검증하고 `manifest.json` 을 만든다(오류가 있으면 해당 이미지를 다시 받는다).
1. ZIP 을 풀어 WebP(1280px, 10MB 이하)로 변환해 `docs/demo-products/photoreal/` 에 둔다.
2. V20 마이그레이션으로 해당 상품의 대표 이미지(A)를 교체하고, B·C 는 상세 이미지로 붙인다. 이미 쓰는 `product_detail_image` 순서를 유지한다.
3. 링크 점검기(`scripts/demo-products/check_links.py`)로 모두 열리는지 확인한다.
