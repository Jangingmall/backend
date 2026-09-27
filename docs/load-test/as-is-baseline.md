# AS-IS 베이스라인

## 환경
- Gatling 20 VU / 30s constant
- PostgreSQL (local-postgresql 프로파일)
- Hibernate statistics: enabled

## ProductListSimulation (최적화 전)

| 지표 | 값 |
|-----|---|
| p50 | 미측정 |
| p95 | 미측정 |
| p99 | 미측정 |
| 최대 RPS | 미측정 |
| 에러율 | 미측정 |

## Hibernate 쿼리 통계 (단일 요청 기준)

| 원인 | 쿼리 수 |
|-----|--------|
| product SELECT + count | 2 |
| @ElementCollection EAGER (giftThemes/purposeTags/colors) × N상품 | N×3 |
| productImage findByProductId per-product | N |
| imageService.publicVariants (findById per image) | N×M |
| **총 쿼리 수 (N=20, M≈3)** | ~142 |

> N = 페이지 상품 수 (size=20), M = 상품당 이미지 수

## 병목 식별

- 애플리케이션 레이어 N+1: ProductService.response() 루프
- JPA @ElementCollection EAGER: giftThemes/purposeTags/colors
- 이미지 단건 조회 반복: ImageService.publicVariants()
