# PR 정보

- 제목 영어, 본문 한글

## 브랜치명

```
feat/product-core
```

## PR 제목

```
feat: implement product domain — CRUD, status transition, list and detail APIs
```

## PR 본문

```markdown
# Related Issues

-

## 작업 리스트

- [x] Product 엔티티, ProductStatus enum, Category, Subcategory 도메인 레이어 구현
- [x] JpaProductRepository, JpaCategoryRepository, JpaSubcategoryRepository Infrastructure 구현
- [x] ProductService — 등록/내목록/전체목록/상세/수정/상태변경/삭제 유스케이스 구현
- [x] ProductController — 7개 엔드포인트 (ARTISAN 역할 기반 @PreAuthorize)
- [x] GlobalExceptionHandler — AccessDeniedException 핸들러 추가 (@PreAuthorize 거부 시 403 반환)
- [x] ProductStatusTest — 상태 전이 허용/불허 9개 케이스
- [x] ProductTest — 엔티티 생성, 가격/재고 유효성, 소유자 권한 검증
- [x] ProductServiceTest — 서비스 레이어 유닛 테스트 (Mockito)
- [x] ProductControllerTest — REST Docs 7개 엔드포인트 + 403 에러 케이스 문서화

## 작업 내용

### 상품 도메인 레이어 (Domain / Infrastructure)

**Product 엔티티**
- `ProductStatus` enum: DRAFT / ON_SALE / SOLD_OUT / HIDDEN
- 상태 전이 규칙 도메인 내 강제 (`validateTransitionTo`) — 허용되지 않은 전이 시 `BusinessRuleViolationException`
- 가격(price > 0), 재고(stock ≥ 0) 유효성을 엔티티 팩토리 메서드에서 검증
- 소유자 검증 (`verifyOwner`) — 타 장인의 수정/삭제 시 `ForbiddenException`

**Category / Subcategory 엔티티**
- product-core 브랜치에서 함께 생성 (feat/product-category에서 조회 API만 추가 예정)

**Repository 구현**
- `ProductRepository` 도메인 인터페이스 → `JpaProductRepository` Infrastructure 구현체
- artisanId별 페이징 조회, status별 페이징 조회

---

### 상품 API (Application / Presentation)

| 메서드 | 경로 | 역할 | 권한 |
|---|---|---|---|
| POST | `/api/products` | 상품 등록 (DRAFT) | ARTISAN |
| GET | `/api/products/me` | 내 상품 목록 | ARTISAN |
| GET | `/api/products` | 전체 목록 (ON_SALE만) | 공개 |
| GET | `/api/products/{productId}` | 상품 상세 | 공개 |
| PATCH | `/api/products/{productId}` | 상품 수정 | ARTISAN (소유자) |
| PATCH | `/api/products/{productId}/status` | 상태 변경 | ARTISAN (소유자) |
| DELETE | `/api/products/{productId}` | 상품 삭제 | ARTISAN (소유자) |

---

### 전역 예외 처리 보완

`@PreAuthorize` 거부(`AccessDeniedException`) 가 기존 `Exception.class` 핸들러로 500 반환되던 문제 수정.
`GlobalExceptionHandler`에 `AccessDeniedException` 핸들러 추가 → 403 반환.

---

### 테스트

| 파일 | 유형 | 케이스 수 |
|---|---|---|
| ProductStatusTest | JunitTest | 9개 (상태 전이 허용/불허 전체) |
| ProductTest | JunitTest / JunitExceptionTest | 6개 (엔티티 생성, 유효성, 권한) |
| ProductServiceTest | JunitTest / JunitExceptionTest | 5개 (서비스 레이어 Mockito) |
| ProductControllerTest | REST Docs | 7개 엔드포인트 + 403 에러 케이스 |

## 참고사항

- 상품 목록(`GET /api/products`)은 현재 ON_SALE 상태만 반환 — 추후 필터 파라미터 확장 가능
- feat/product-category 머지 후 카테고리 조회 API 추가 예정
- feat/product-wish, feat/product-qna, feat/product-review는 이 PR 머지 후 병렬 시작 가능
```
