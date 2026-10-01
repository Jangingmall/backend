# 시연용 상품 사진

AI 상세페이지 생성 시연에 쓰는 사진이다. 저장소가 공개라서 `develop` 브랜치의 raw 주소로 누구나 내려받을 수 있다.
요청의 `images`에는 이 https 주소를 그대로 넣을 수 있다(이미지 ID 대신 URL 형태).

## Flow 1 — 전주 합죽선 · 매화선 (9장, 같은 사진은 없음)
기준 주소: `https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-images/hapjukseon-maehwa/`

| 파일 | 설명 |
|---|---|
| `01-hero.webp` | 대표: 거치대에 세운 부채 |
| `02-packshot.webp` | 부채 조각(포장 컷) |
| `03-detail.webp` | 부챗살·매듭 확대 |
| `04-lifestyle.webp` | 선물 상자 속 부채 |
| `05-lifestyle-02.webp` | 탁자 위 부채(리넨) |
| `06-detail-02.webp` | 부채 정면 |
| `07-detail-03.webp` | 선면 매화 확대 |
| `08-detail-04.webp` | 탁자 위 부채(근접) |
| `09-detail-05.webp` | 평평하게 놓은 부채와 다기 |

- 대표 사진(첫 장)이 AI 상품 분석에 쓰인다. AI는 최대 12장까지 받는다.
- 상품명·설명 문구는 시연 계획서(Flow 1)를 따른다.

## 대표 이미지 출처 (외부 링크가 기본)
소분류 일러스트 56장(`docs/seed-images/sub-NN.webp`)은 저장소 raw 주소로 서비스한다. 서버(S3/CDN)가 꺼져 있어도 열리고, 올린 뒤 24시간 만료가 없다.
- 형식·크기는 이미지 규격과 같다: WebP, 10MB 이하, 10,000px 이하(실제 800×800, 파일당 약 10KB).
- 기준 주소: `https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/seed-images/sub-NN.webp` (`develop`에 머지된 뒤부터 열린다)
- S3 배포 버전과 서로 바꾸려면 `switch-image-source.sql`을 쓴다.
- 이 링크는 저장소가 공개일 때만 열린다. 저장소를 비공개로 바꾸면 깨진다.

## 직접 그린 일러스트 (출처 확인이 필요한 사진 대신 쓰는 안전한 이미지)
외부에서 받은 사진은 출처·내용을 확인할 수 없어, 공개된 사실을 바탕으로 직접 그린 그림을 따로 두었다. 사람·실제 사진·상표가 없다.
생성: `python3 scripts/demo-images/draw_demo_products.py` (pillow + 한글 글꼴 필요, 결과는 이 폴더에 저장된다).

| 폴더 | 내용 | 반영한 사실 |
|---|---|---|
| `hapjukseon-maehwa-illustrated/` (9장) | 전주 합죽선 · 매화선: 펼친 모습, 접은 모습, 부챗살·변죽 확대, 선면 매화, 제작 과정, 재료, 선물 포장 | 겉대 두 쪽을 붙인 접부채, 부챗살 보통 38개, 변죽은 대나무 7쪽·매화 새김, 민어풀·닥나무 한지(선자지), 사북·선추, 전주 선자청 |
| `cheongja-bunjeong-teacup/` (6장) | 청자 · 분청 찻잔: 운학문 청자, 귀얄·철화 분청, 한 쌍, 장식 기법, 굽, 선물 상자 | 비색 유약·상감·운학문, 분청은 백토 분장과 상감·인화·박지·조화·철화·귀얄·담금 7가지 기법 |

- 대표 사진은 각 폴더의 첫 장(`01-…`)이다.
- 기준 주소: `https://raw.githubusercontent.com/Jangingmall/backend/develop/docs/demo-images/<폴더>/<파일>`
