# 산출물 2 — 상품·카탈로그·검색·필터 API

---

## ProductController.java
`src/main/java/com/jangingmall/backend/product/presentation/ProductController.java`

```java
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    @GetMapping
    public ApiResponse<Page<ProductResponse>> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) Long subcategoryId,
        @RequestParam(required = false) String giftTheme,
        @RequestParam(required = false) String sort,
        @RequestParam(required = false) Integer minPrice,
        @RequestParam(required = false) Integer maxPrice,
        @RequestParam(required = false) Boolean excludeSoldOut,
        @RequestParam(required = false) Long artisanId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        ProductCommand.Search search = new ProductCommand.Search(
            keyword, categoryId, subcategoryId, giftTheme, sort,
            minPrice, maxPrice, excludeSoldOut, artisanId
        );
        return ApiResponse.ok(productService.findOnSale(search, pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody ProductRequest.Create request
    ) {
        ProductCommand.Create command = new ProductCommand.Create(
            memberId, request.categoryId(), request.subcategoryId(), request.title(), request.description(),
            request.price(), request.stock(), request.thumbnailUrl(),
            request.giftThemes() != null ? request.giftThemes() : List.of(),
            request.purposeTags() != null ? request.purposeTags() : List.of(),
            request.productionPeriodDays(),
            request.colors() != null ? request.colors() : List.of(),
            request.images()
        );
        return ResponseEntity.status(201).body(ApiResponse.created(productService.create(command)));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> detail(@PathVariable Long productId) {
        return ApiResponse.ok(productService.findById(productId));
    }

    @PatchMapping("/{productId}")
    @PreAuthorize("hasRole('ARTISAN')")
    public ApiResponse<ProductResponse> update(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long productId,
        @Valid @RequestBody ProductRequest.Update request
    ) {
        ProductCommand.Update command = new ProductCommand.Update(
            productId, memberId, request.categoryId(), request.subcategoryId(), request.title(), request.description(),
            request.price(), request.stock(), request.thumbnailUrl(),
            request.giftThemes() != null ? request.giftThemes() : List.of(),
            request.purposeTags() != null ? request.purposeTags() : List.of(),
            request.productionPeriodDays(),
            request.colors() != null ? request.colors() : List.of(),
            request.images()
        );
        return ApiResponse.ok(productService.update(command));
    }

    @PostMapping("/{productId}/wish")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> wish(@AuthenticationPrincipal Long memberId, @PathVariable Long productId) {
        memberActivityService.wish(memberId, productId);
        return ApiResponse.noContent();
    }

    @DeleteMapping("/{productId}/wish")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> unwish(@AuthenticationPrincipal Long memberId, @PathVariable Long productId) {
        memberActivityService.unwish(memberId, productId);
        return ApiResponse.noContent();
    }

    @GetMapping("/{productId}/reviews")
    public ApiResponse<Page<ProductReviewResponse.ReviewView>> reviews(
        @PathVariable Long productId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(productReviewService.findReviews(productId, pageable));
    }

    @PostMapping("/{productId}/reviews")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ProductReviewResponse.ReviewView>> writeReview(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long productId,
        @Valid @RequestBody ProductReviewRequest.Write request
    ) {
        ProductReviewResponse.ReviewView response = productReviewService.write(
            new ProductReviewCommand.Write(productId, memberId, request.orderItemId(), request.rating(),
                request.content(), request.images())
        );
        return ResponseEntity.status(201).body(ApiResponse.created(response));
    }
}
```

---

## ProductCommand.java (Search record)
`src/main/java/com/jangingmall/backend/product/application/ProductCommand.java`

```java
public sealed interface ProductCommand {

    record Search(
        String keyword,
        Long categoryId,
        Long subcategoryId,
        String giftTheme,
        String sort,
        Integer minPrice,
        Integer maxPrice,
        Boolean excludeSoldOut,
        Long artisanId
    ) implements ProductCommand {}

    record Create(
        Long artisanId, Long categoryId, Long subcategoryId, String title, String description,
        int price, int stock, String thumbnailUrl, List<String> giftThemes, List<String> purposeTags,
        Integer productionPeriodDays, List<String> colors, List<String> images
    ) implements ProductCommand {}

    record Update(
        Long productId, Long requesterId, Long categoryId, Long subcategoryId, String title, String description,
        int price, int stock, String thumbnailUrl, List<String> giftThemes, List<String> purposeTags,
        Integer productionPeriodDays, List<String> colors, List<String> images
    ) implements ProductCommand {}
}
```

---

## JpaProductRepository.java (동적 검색·필터 JPQL)
`src/main/java/com/jangingmall/backend/product/infrastructure/JpaProductRepository.java`

```java
@Repository
class JpaProductRepository implements ProductRepository {

    private static final List<ProductStatus> ON_SALE_STATUSES = List.of(ProductStatus.ON_SALE);
    private static final List<ProductStatus> INCLUDE_SOLD_OUT = List.of(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT);

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Product> findOnSale(ProductCommand.Search search, Pageable pageable) {
        List<ProductStatus> statuses = Boolean.TRUE.equals(search.excludeSoldOut()) ? ON_SALE_STATUSES : INCLUDE_SOLD_OUT;
        StringBuilder where = buildWhere(search, statuses);
        String orderClause = resolveOrder(search.sort(), pageable);

        TypedQuery<Product> query = entityManager.createQuery(
            "SELECT p FROM Product p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.subcategory"
                + where + orderClause, Product.class);
        TypedQuery<Long> countQuery = entityManager.createQuery(
            "SELECT count(p) FROM Product p" + where, Long.class);

        applyParameters(query, search, statuses);
        applyParameters(countQuery, search, statuses);

        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        return new PageImpl<>(query.getResultList(), pageable, countQuery.getSingleResult());
    }

    private StringBuilder buildWhere(ProductCommand.Search search, List<ProductStatus> statuses) {
        StringBuilder where = new StringBuilder(" WHERE p.status IN :statuses");
        if (search.keyword() != null && !search.keyword().isBlank()) where.append(" AND p.title LIKE :keyword");
        if (search.categoryId() != null)                               where.append(" AND p.category.id = :categoryId");
        if (search.subcategoryId() != null)                            where.append(" AND p.subcategory.id = :subcategoryId");
        if (search.giftTheme() != null && !search.giftTheme().isBlank()) where.append(" AND :giftTheme MEMBER OF p.giftThemes");
        if (search.minPrice() != null)                                 where.append(" AND p.price >= :minPrice");
        if (search.maxPrice() != null)                                 where.append(" AND p.price <= :maxPrice");
        if (search.artisanId() != null)                                where.append(" AND p.artisanId = :artisanId");
        return where;
    }

    private <T> void applyParameters(TypedQuery<T> query, ProductCommand.Search search, List<ProductStatus> statuses) {
        query.setParameter("statuses", statuses);
        if (search.keyword() != null && !search.keyword().isBlank()) query.setParameter("keyword", "%" + search.keyword() + "%");
        if (search.categoryId() != null)    query.setParameter("categoryId", search.categoryId());
        if (search.subcategoryId() != null) query.setParameter("subcategoryId", search.subcategoryId());
        if (search.giftTheme() != null && !search.giftTheme().isBlank()) query.setParameter("giftTheme", search.giftTheme());
        if (search.minPrice() != null)  query.setParameter("minPrice", search.minPrice());
        if (search.maxPrice() != null)  query.setParameter("maxPrice", search.maxPrice());
        if (search.artisanId() != null) query.setParameter("artisanId", search.artisanId());
    }

    private String resolveOrder(String sort, Pageable pageable) {
        if (sort != null) {
            return switch (sort) {
                case "PRICE_ASC"  -> " ORDER BY p.price ASC, p.id DESC";
                case "PRICE_DESC" -> " ORDER BY p.price DESC, p.id DESC";
                case "POPULAR"    -> " ORDER BY p.id DESC";
                default           -> " ORDER BY p.createdAt DESC, p.id DESC";
            };
        }
        return " ORDER BY p.createdAt DESC, p.id DESC";
    }
}
```

---

## CategoryController.java
`src/main/java/com/jangingmall/backend/product/presentation/CategoryController.java`

```java
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class CategoryController {

    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse.CategoryItem>> categories() {
        return ApiResponse.ok(categoryQueryService.findAllCategories());
    }

    @GetMapping("/categories/main")
    public ApiResponse<List<CategoryResponse.CategoryItem>> mainCategories() {
        return ApiResponse.ok(categoryQueryService.findAllCategories());
    }

    @GetMapping("/subcategories")
    public ApiResponse<List<CategoryResponse.SubcategoryItem>> subcategories() {
        return ApiResponse.ok(categoryQueryService.findAllSubcategories());
    }

    @GetMapping("/materials")
    public ApiResponse<List<String>> materials(@RequestParam(required = false) Long subcategoryId) {
        return ApiResponse.ok(categoryQueryService.findMaterials(subcategoryId));
    }
}
```
