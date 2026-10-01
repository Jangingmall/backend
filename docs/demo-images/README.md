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
