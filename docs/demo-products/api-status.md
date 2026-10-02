# 프론트가 부르는 API 와 백엔드 상태

프론트(`frontend/src/app/page.tsx`, `types/sort.ts`) 기준. 정렬 ENUM 설명은 REST Docs(`product-list`, `exhibition-list`, `exhibition-detail`)에도 들어 있다.

| 화면 | 프론트 호출 | 백엔드 | 비고 |
|---|---|---|---|
| 홈 베스트 5 | `GET /api/products?sort=SALES_COUNT&size=5` | 구현됨(판매 완료 주문 수량 합) | V28 이 베스트 5개에 시연 주문을 심음 |
| 홈 신상품 4 | `sort=NEWEST&size=4` | 구현됨 | |
| 홈 기획전 | 프론트는 지금 `sort=WISHLIST_COUNT&size=4` | 찜순도 구현됨. 전용 API `GET /api/exhibitions` 추가 | 프론트가 전용 API 로 바꾸면 제목·배너·상품 묶음이 나온다 |
| 전체 목록 기본 | `sort=POPULAR` | 구현됨(가중치+판매×3+찜×2+리뷰) | V28 이 맨 앞 8개를 고정 |
| 상품 상세 | `GET /api/products/{id}` | `detailPageBlocks`·`detailPageDocument`·`detailFeatures` | 후 둘은 상세 조회에서만 내려감 |

정렬 ENUM: `NEWEST` `POPULAR` `SALES_COUNT` `WISHLIST_COUNT` `PRICE_ASC` `PRICE_DESC` (소문자·하이픈 표기도 읽음).
기획전 목록 `DISPLAY_ORDER` `NEWEST`, 기획전 상품 `CURATED` `NEWEST` `PRICE_ASC` `PRICE_DESC`.

## 고화질 AI 이미지(베스트 5·기획전 4)
적용 완료: 9개 상품 × 3장(`photoreal/staging/home_best1_A.webp` … `home_plan4_C.webp`)과 기획전 배너 2장(`banner_ex1`, `banner_ex2`).
A 는 대표 이미지, B·C 는 상세 이미지로 V23 에 들어가고(`seed_design_products.py`), 상세 JSON(V26)은 3장짜리 틀로 만들어진다. 기획전 배너는 V29 에 들어 있다.
