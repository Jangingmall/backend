# 장인몰 API 계약서 — 공개 조회 4종

대상: `GET /api/products`(PRODUCT-001), `GET /api/products/{productId}`(PRODUCT-002),
`GET /api/member/artisans`(ARTISAN-010), `GET /api/member/artisans/{artisanId}`(ARTISAN-001)

타입 표기는 백엔드 구현 타입(Java) 기준입니다.

---

## 0. 전역 규칙

### 0-1. ID 타입 통일

**모든 ID 필드는 `Long`입니다.** Path/Query에 실리는 ID(`artisanId`, `productId` 등)는 HTTP 전송 특성상 쿼리스트링/경로에서는 문자열로 옮겨지지만, **논리 타입은 항상 Long이고 서버는 이를 Long으로 파싱**합니다. 숫자가 아닌 값이 오면 `400 INVALID_INPUT`입니다.

예외 — Long이 아닌 식별자:
| 필드 | 타입 | 이유 |
| --- | --- | --- |
| `imageId` | String | ULID 형식 (예: `image_01HXYZ`) |
| `sessionId` | String | 챗봇 세션 ID |
| `orderNumber` | String | 사람이 보는 주문번호. `orderId`(Long)와는 별개 식별자 |

### 0-2. 금액 필드

`price`, `priceDelta`, `amount`, `totalAmount` 등 모든 금액 필드는 **`Long`**입니다. 원화는 소수 단위가 없어 `BigDecimal`을 쓰지 않습니다.

### 0-3. 평점

`rating`은 **`BigDecimal`**입니다(소수점 표현, 예: `4.8`). 후기가 0건이면 `null`입니다.

### 0-4. 공통 응답 봉투

```
ApiResponse<T>
  success: Boolean   - required, not nullable, 항상 true (실패 시 별도 에러 응답)
  status:  Integer   - required, not nullable, 실제 HTTP 상태코드와 동일
  data:    T         - required, nullable (본문 없는 응답은 null)

ApiErrorResponse
  success:   Boolean - required, not nullable, 항상 false
  status:    Integer - required, not nullable
  errorCode: String  - required, not nullable
    - enum: INVALID_INPUT, REQUEST_INVALID, REQUEST_BODY_MALFORMED, UNAUTHORIZED,
            FORBIDDEN, NOT_FOUND, CONFLICT, BUSINESS_RULE_VIOLATION, TOO_MANY_REQUESTS,
            INTERNAL_ERROR, EXPIRED, MISMATCH, CONCURRENT_UPDATE

PagedResponse<T>
  items:       List<T>  - required, not nullable, 빈 배열 가능
  nextCursor:  String   - optional, nullable, 다음 페이지 없으면 null
  hasNext:     Boolean  - required, not nullable
  totalCount:  Integer  - required, not nullable, >= 0
```

---

## 1. `GET /api/products` — 상품 목록

### Query Parameters

```
cursor: String
  - optional
  - nullable
  - 이전 응답의 nextCursor 값. 첫 페이지는 생략

limit: Integer
  - optional
  - not nullable
  - default: 20
  - min: 1
  - max: 100

artisanId: Long
  - optional
  - nullable
  - 특정 장인 작품만 필터링

category: String
  - optional
  - nullable
  - 동적 값 — 고정 enum 아님. 유효 목록은 GET /api/products/categories(PRODUCT-016)로 조회

material: String
  - optional
  - nullable
  - 동적 값 — 고정 enum 아님. 유효 목록은 GET /api/products/materials(PRODUCT-017)로 조회

giftTheme: String
  - optional
  - nullable
  - enum: HOUSEWARMING, WEDDING, PARENTS, PROMOTION, BIRTHDAY_60TH, BOSS, FRIEND, CORPORATE
    (한글 라벨: 집들이/웨딩/부모님/승진/돌·환갑/상사/친구/기업·단체 — 영문 코드는 BE 임의 지정, PM 확인 필요)

color: String
  - optional
  - nullable
  - enum: WHITE, BLACK, GRAY, RED, BLUE, GREEN, BROWN
    (PM 자료에 "등"이 붙어있어 확정된 전체 목록인지 재확인 필요)

sort: String
  - optional
  - not nullable
  - default: POPULAR
  - enum: POPULAR, NEWEST, WISHLIST_COUNT, SALES_COUNT, PRICE_ASC, PRICE_DESC

minPrice: Long
  - optional
  - nullable
  - >= 0

maxPrice: Long
  - optional
  - nullable
  - >= 0
  - maxPrice >= minPrice (둘 다 있을 때)

isLimited: Boolean
  - optional
  - nullable

isCustomOrder: Boolean
  - optional
  - nullable

hasGiftWrap: Boolean
  - optional
  - nullable

excludeSoldOut: Boolean
  - optional
  - not nullable
  - default: true

keyword: String
  - optional
  - nullable
  - max length: 미정(확인 필요)
```

### Response — `data: PagedResponse<ProductListItem>`

```
ProductListItem
  productId:     Long      - required, not nullable
  name:          String    - required, not nullable
  price:         Long      - required, not nullable, >= 0
  thumbnailUrl:  String    - required, not nullable
  status:        String    - required, not nullable
    - enum: DRAFT, ON_SALE, SOLD_OUT, HIDDEN
    - 주의: 공개 목록 API 응답에는 ON_SALE | SOLD_OUT만 나옴(DRAFT/HIDDEN은 노출 안 됨).
      HIDDEN을 MVP에 포함할지는 PM 확인 중
  category:      String    - required, not nullable  (동적 값, 0-3-category 참고)
  color:         String    - optional, nullable
  giftTheme:     String    - optional, nullable
  rating:        BigDecimal - optional, nullable, 후기 없으면 null (0 아님)
  isLimited:     Boolean   - required, not nullable
  isCustomOrder: Boolean   - required, not nullable
  isSingleItem:  Boolean   - required, not nullable
  isNew:         Boolean   - required, not nullable  (등록 14일 이내, 기준일수 미확정)
  hasGiftWrap:   Boolean   - required, not nullable
  hasOptions:    Boolean   - required, not nullable
  purposeTags:   List<String> - required, not nullable, 빈 배열 가능
  artisanId:     Long      - required, not nullable
  artisanName:   String    - required, not nullable
```

---

## 2. `GET /api/products/{productId}` — 상품 상세

### Path Parameter

```
productId: Long
  - required
  - not nullable
```

### Response — `data: ProductDetail` (위 `ProductListItem` 필드 전부 + 아래)

```
ProductDetail
  modelName:            String  - optional, nullable
  size:                 String  - optional, nullable
  material:             String  - required, not nullable  (동적 값)
  components:           List<String> - required, not nullable, 빈 배열 가능
  manufacturer:         String  - required, not nullable
  countryOfOrigin:       String  - required, not nullable
  asManagerContact:     String  - optional, nullable
  warrantyPolicy:       String  - optional, nullable
  description:          String  - required, not nullable
  stock:                Integer - required, not nullable, >= 0
    - 옵션 없는 단순상품일 때만 유효. 옵션 있으면 optionGroups[].choices[].stock이 실제 재고
  stockAvailable:       Boolean - required, not nullable
  productionPeriodDays: Integer - required, not nullable, >= 0
  engravingAvailable:   Boolean - required, not nullable
  shippingFee:          Long    - required, not nullable, >= 0
  freeShippingThreshold: Long   - optional, nullable, >= 0
  detailPageBlocks:     List<ContentBlock> - required, not nullable, min size 1
    - ON_SALE 상태에서만 유효한 값
  optionGroups:         List<ProductOptionGroup> - required, not nullable, 빈 배열 가능
  images:                List<ProductImage> - required, not nullable, min size 1
  artisan:               ProductArtisanSummary - required, not nullable
```

```
ProductOptionGroup
  optionGroupId: Long    - required, not nullable
  type:          String  - required, not nullable
    - enum: REQUIRED, OPTIONAL, TEXT
  name:          String  - required, not nullable
  maxLength:     Integer - optional, nullable  (type === TEXT 일 때만 값 존재)
  choices:       List<ProductOptionChoice> - required, not nullable
    - type === TEXT 이면 항상 빈 배열

ProductOptionChoice
  choiceId:              Long    - required, not nullable
  name:                  String  - required, not nullable
  priceDelta:            Long    - required, not nullable  (기본가 대비 가감액, 음수 가능)
  stock:                 Integer - optional, nullable, >= 0  (OPTIONAL 그룹이 재고 무제한이면 null)
  productionPeriodDays:  Integer - optional, nullable, >= 0  (옵션별로 상이할 때만 값 존재)
```

```
ContentBlock
  order:     Integer - required, not nullable, >= 1
  tag:       String  - required, not nullable
    - enum: h2, p, img, video
  hasImage:  Boolean - required, not nullable
  imageUrl:  String  - optional, nullable  (tag === img 일 때만 값)
  videoUrl:  String  - optional, nullable  (tag === video 일 때만 값, URL 등록 방식)
  text:      String  - optional, nullable  (tag === h2 | p 일 때만 값)
```

```
ProductImage
  imageId:  String                    - required, not nullable  (ULID)
  alt:      String                    - required, not nullable
  variants: List<ProductImageVariant> - required, not nullable, size 3 (320w/640w/1280w 고정)

ProductImageVariant
  url:    String  - required, not nullable
  width:  Integer - required, not nullable
    - enum: 320, 640, 1280
  height: Integer - required, not nullable
  format: String  - required, not nullable
    - enum: webp
```

```
ProductArtisanSummary
  artisanId:           Long   - required, not nullable
  businessName:        String - required, not nullable
  introduction:        String - required, not nullable
  certificationLevel:  String - required, not nullable
    - enum: 보유자, 전승교육사, 이수자, 일반
```

### 대표 에러

```
404 NOT_FOUND
  - 존재하지 않는 상품
  - DRAFT 상태 상품(콘텐츠 게시 전) — 항상 404
  - HIDDEN 상태 상품 — MVP 포함 여부 자체가 미정
```

---

## 3. `GET /api/member/artisans` — 장인 목록

### Query Parameters

```
cursor: String
  - optional / nullable / 이전 응답 nextCursor 값

limit: Integer
  - optional / not nullable / default: 20 / min: 1 / max: 100

certificationLevel: List<String>
  - optional
  - nullable
  - 다중선택. 파라미터 미전달 시 기본 ALL(전체노출)
  - enum(개별 원소): 보유자, 전승교육사, 이수자, 일반

category: List<String>
  - optional
  - nullable
  - 다중선택. 파라미터 미전달 시 기본 ALL(전체노출)
  - 동적 값(0-3-category 참고)

initial: String
  - optional
  - nullable
  - 초성(ㄱ~ㅎ) 단일 문자
  - 최종 채택 여부 확인 중 — 스펙에서 빠질 수 있음

sort: String
  - optional / not nullable / default: POPULAR
  - enum: POPULAR, MOST_PRODUCTS, RECENTLY_JOINED
```

### Response — `data: PagedResponse<ArtisanListItem>`

```
ArtisanListItem
  artisanId:           Long    - required, not nullable
  businessName:        String  - required, not nullable
  introduction:        String  - optional, nullable
  profileImageUrl:     String  - optional, nullable
  certificationLevel:  String  - required, not nullable
    - enum: 보유자, 전승교육사, 이수자, 일반
  isOrganization:      Boolean - required, not nullable  ("(단체)" 접미사 표기용)
  category:            String  - required, not nullable  (동적 값)
  region:              String  - optional, nullable
  careerYears:         Integer - optional, nullable, >= 0
  productCount:        Integer - required, not nullable, >= 0
  topProducts:         List<ArtisanTopProduct> - required, not nullable, max size 3

ArtisanTopProduct
  productId:     Long   - required, not nullable
  thumbnailUrl:  String - required, not nullable
```

---

## 4. `GET /api/member/artisans/{artisanId}` — 장인 상세

### Path Parameter

```
artisanId: Long
  - required
  - not nullable
```

### Response — `data: ArtisanDetail` (위 `ArtisanListItem` 필드 전부 + 아래)

```
ArtisanDetail
  certifiedYear:   Integer - optional, nullable
  lineage:         String  - optional, nullable
  quote:           String  - optional, nullable  (장인이 아직 안 채웠으면 null)
  bio:             String  - optional, nullable
  videoUrl:        String  - optional, nullable
  careerTimeline:  List<CareerTimelineItem> - required, not nullable, 빈 배열 가능

CareerTimelineItem
  year:         Integer - required, not nullable
  title:        String  - required, not nullable
  description:  String  - required, not nullable
```

### 대표 에러

```
404 NOT_FOUND
  - 존재하지 않는 장인
  - 활동중지 장인의 공개 여부는 아직 PM 확정 전(R-13 관련)
```

---

## 5. 아직 확정 안 된 것 (임의로 채우지 않고 남겨둠)

| 항목 | 상태 |
| --- | --- |
| `giftTheme`의 영문 코드값(`HOUSEWARMING` 등) | BE 임의 지정, PM 확정 필요 |
| `color` 전체 목록 | PM 자료에 "등" 표기, 확정 목록인지 재확인 필요 |
| `initial`(초성 필터) 최종 채택 여부 | v1.3/v1.4 문서에 언급 없음 |
| `keyword` 최대 길이 | 미정 |
| `category`/`material` 값의 유효성 검증 규칙(오타·존재하지 않는 코드 처리) | 미정 — 400으로 막을지, 빈 결과로 처리할지 |
