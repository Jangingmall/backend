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

## 대표 이미지 출처 전환 (S3 배포 버전 ↔ 외부 URI 버전)
소분류 일러스트 56장은 S3(`img.stg.midam.store`)와 저장소 raw 주소 두 곳에서 서비스할 수 있다. `switch-image-source.sql`로 한 번에 바꾼다.
- S3 버전은 상품에 연결하지 않으면 24시간 뒤 삭제된다. 만료가 걱정되면 외부 URI 버전으로 전환해 둔다.
- 외부 URI 버전은 만료가 없지만 GitHub 서버에 의존한다.
