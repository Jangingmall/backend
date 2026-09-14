# PR 정보

- 제목 영어, 본문 한글

## 브랜치명

```
feat/product-category
```

## PR 제목

```
feat: implement category, subcategory, and material query APIs
```

## PR 본문

```markdown
# Related Issues

-

## 작업 리스트

- [x] Product 엔티티에 `material` 컬럼 추가
- [x] `ProductRepository` — subcategoryId 기준 소재 DISTINCT 조회 추가
- [x] `CategoryQueryService` — 카테고리/서브카테고리/소재 목록 조회
- [x] `CategoryController` — 4개 엔드포인트 구현
- [x] `CategoryControllerTest` — REST Docs 4개 엔드포인트 문서화

## 작업 내용

### API 목록

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| GET | `/api/products/categories` | 전체 카테고리 목록 | 공개 |
| GET | `/api/products/categories/main` | 메인 카테고리 목록 | 공개 |
| GET | `/api/products/subcategories` | 전체 서브카테고리 목록 | 공개 |
| GET | `/api/products/materials` | 소재 목록 (subcategoryId 필터) | 공개 |

### 소재 목록 조회 동작

- `subcategoryId` 쿼리 파라미터로 해당 종목에서 실제 사용 중인 소재만 반환
- `subcategoryId` 미전달 시 빈 배열 반환 (null 방어 처리)
- product 테이블의 `material` 컬럼에서 DISTINCT 조회 — 별도 소재 테이블 없음

### Product 엔티티 변경

- `material VARCHAR(50)` 컬럼 추가 (nullable — 소재 미지정 상품 허용)

## 참고사항

- `GET /categories/main`은 현재 전체 카테고리와 동일 — 추후 PM 확정 시 메인 노출 플래그 분리 가능
- 소재는 enum이 아닌 자유 문자열 코드 — 카테고리별로 다른 소재 집합을 동적으로 반환하는 구조
```
