package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.application.MemberReadRepository;
import com.jangingmall.backend.member.application.SellerApplicationData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import com.jangingmall.backend.member.domain.ArtisanProfile;
import com.jangingmall.backend.member.domain.ArtisanSubscription;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.domain.MemberStatus;
import com.jangingmall.backend.member.domain.RecentView;
import com.jangingmall.backend.member.domain.SellerApplication;
import com.jangingmall.backend.member.domain.Wishlist;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductReview;
import com.jangingmall.backend.product.domain.ProductStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * Member read model implemented with JPQL projections.
 *
 * <p>Database-specific JSON/native SQL is intentionally avoided. Mapped entities are queried with
 * JPA and the response projection is assembled in Java, without a second SQL-mapping layer.</p>
 */
@Repository
public class MemberReadRepositoryImpl implements MemberReadRepository {
    private static final Set<ProductStatus> VISIBLE_PRODUCTS = Set.of(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT);
    private static final String APPROVED = "APPROVED";

    @PersistenceContext
    private EntityManager entityManager;

    private final String cdn;

    public MemberReadRepositoryImpl(@Value("${member.image-base-url:http://localhost:8080/media}") String cdn) {
        this.cdn = cdn.replaceAll("/+$", "");
    }

    @Override
    public Page<SellerApplicationData> applications(Pageable pageable, String status) {
        SellerApplication.Status filter = applicationStatus(status);
        String condition = filter == null ? "" : " AND a.status=:status";
        var query = entityManager.createQuery(
                "SELECT a FROM SellerApplication a WHERE 1=1" + condition + " ORDER BY a.id DESC",
                SellerApplication.class)
            .setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
        var count = entityManager.createQuery(
            "SELECT count(a) FROM SellerApplication a WHERE 1=1" + condition, Long.class);
        if (filter != null) {
            query.setParameter("status", filter);
            count.setParameter("status", filter);
        }
        List<SellerApplicationData> items = query.getResultList().stream()
            .map(SellerApplicationData::from).toList();
        return new PageImpl<>(items, pageable, count.getSingleResult());
    }

    @Override
    public Page<Map<String, Object>> wishes(Long memberId, Pageable pageable) {
        String joins = " FROM Wishlist w, Product p, ArtisanProfile a, Member m"
            + " WHERE w.productId=p.id AND p.artisanId=a.id AND m.id=a.id"
            + " AND w.memberId=:memberId"
            + " AND p.status IN :statuses AND m.status=:active AND a.certificationStatus=:approved";
        List<Object[]> rows = entityManager.createQuery(
                "SELECT w,p,a" + joins + " ORDER BY w.id DESC", Object[].class)
            .setParameter("memberId", memberId)
            .setParameter("statuses", VISIBLE_PRODUCTS).setParameter("active", MemberStatus.ACTIVE)
            .setParameter("approved", APPROVED)
            .setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize())
            .getResultList();
        long total = entityManager.createQuery("SELECT count(w)" + joins, Long.class)
            .setParameter("memberId", memberId)
            .setParameter("statuses", VISIBLE_PRODUCTS).setParameter("active", MemberStatus.ACTIVE)
            .setParameter("approved", APPROVED).getSingleResult();
        List<Map<String, Object>> items = rows.stream()
            .map(row -> product((Product) row[1], (ArtisanProfile) row[2])).toList();
        return new PageImpl<>(items, pageable, total);
    }

    @Override
    public Page<Map<String, Object>> orders(Long memberId, Pageable pageable, String status) {
        String filter = orderStatus(status);
        String filtered = "ALL".equals(filter) ? "" : " AND o.status=:status";
        var query = entityManager.createQuery(
                "SELECT o FROM MemberOrderView o WHERE o.memberId=:memberId"
                    + filtered + " ORDER BY o.id DESC", MemberOrderView.class)
            .setParameter("memberId", memberId)
            .setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
        var count = entityManager.createQuery(
                "SELECT count(o) FROM MemberOrderView o WHERE o.memberId=:memberId" + filtered, Long.class)
            .setParameter("memberId", memberId);
        if (!"ALL".equals(filter)) {
            query.setParameter("status", filter);
            count.setParameter("status", filter);
        }
        List<Map<String, Object>> items = query.getResultList().stream().map(this::orderSummary).toList();
        return new PageImpl<>(items, pageable, count.getSingleResult());
    }

    @Override
    public Optional<Map<String, Object>> order(Long memberId, Long orderId) {
        return entityManager.createQuery(
                "SELECT o FROM MemberOrderView o WHERE o.id=:id AND o.memberId=:memberId", MemberOrderView.class)
            .setParameter("id", orderId).setParameter("memberId", memberId).getResultStream().findFirst()
            .map(this::orderDetail);
    }

    @Override
    public Page<Map<String, Object>> reviews(Long memberId, Pageable pageable, boolean writable) {
        return writable ? writableReviews(memberId, pageable) : writtenReviews(memberId, pageable);
    }

    @Override
    public Page<Map<String, Object>> recentViews(Long memberId, Pageable pageable) {
        String joins = " FROM RecentView rv, Product p, ArtisanProfile a, Member m"
            + " WHERE rv.productId=p.id AND p.artisanId=a.id AND m.id=a.id"
            + " AND rv.memberId=:memberId AND p.status IN :statuses"
            + " AND m.status=:active AND a.certificationStatus=:approved";
        List<Object[]> rows = entityManager.createQuery(
                "SELECT rv,p,a" + joins + " ORDER BY rv.viewedAt DESC,rv.id DESC", Object[].class)
            .setParameter("memberId", memberId).setParameter("statuses", VISIBLE_PRODUCTS)
            .setParameter("active", MemberStatus.ACTIVE).setParameter("approved", APPROVED)
            .setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize())
            .getResultList();
        long total = entityManager.createQuery("SELECT count(rv)" + joins, Long.class)
            .setParameter("memberId", memberId).setParameter("statuses", VISIBLE_PRODUCTS)
            .setParameter("active", MemberStatus.ACTIVE).setParameter("approved", APPROVED)
            .getSingleResult();
        List<Map<String, Object>> items = rows.stream().map(row -> {
            RecentView view = (RecentView) row[0];
            Map<String, Object> value = product((Product) row[1], (ArtisanProfile) row[2]);
            value.put("viewedAt", view.getViewedAt());
            return value;
        }).toList();
        return new PageImpl<>(items, pageable, total);
    }

    @Override
    public Page<Map<String, Object>> artisans(Pageable pageable, String certification,
            String category, String initial, String sort) {
        String metric = artisanMetric(sort);
        StringBuilder where = new StringBuilder(
            " FROM ArtisanProfile a, Member m WHERE m.id=a.id AND m.status=:active"
                + " AND m.role=:artisanRole AND a.certificationStatus=:approved");
        if (hasText(certification)) where.append(" AND a.certificationLevel=:certification");
        if (hasText(category)) where.append(" AND a.category=:category");
        InitialRange range = initialRange(initial);
        if (range != null) where.append(" AND a.businessName>=:initialStart AND a.businessName<:initialEnd");

        String expression = switch (metric) {
            case "POPULAR" -> "a.popularityScore";
            case "MOST_PRODUCTS" -> "(SELECT count(p) FROM Product p WHERE p.artisanId=a.id AND p.status IN :statuses)";
            case "RECENTLY_JOINED" -> "m.createdAt";
            default -> throw new DomainException(ErrorCode.INVALID_INPUT);
        };

        var query = entityManager.createQuery(
            "SELECT a,m," + expression + where
                + " ORDER BY " + expression + " DESC,a.id DESC", Object[].class);
        setArtisanParameters(query, certification, category, range, "MOST_PRODUCTS".equals(metric));
        query.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
        List<Object[]> rows = query.getResultList();

        var count = entityManager.createQuery("SELECT count(a)" + where, Long.class);
        setArtisanParameters(count, certification, category, range, false);
        long total = count.getSingleResult();
        List<Map<String, Object>> items = rows.stream()
            .map(row -> artisan((ArtisanProfile) row[0], ((Number) row[2]).longValue())).toList();
        return new PageImpl<>(items, pageable, total);
    }

    @Override
    public Optional<Map<String, Object>> artisan(Long artisanId) {
        return entityManager.createQuery(
                "SELECT a FROM ArtisanProfile a, Member m WHERE a.id=:id AND m.id=a.id"
                    + " AND m.status=:active AND m.role=:artisanRole AND a.certificationStatus=:approved",
                ArtisanProfile.class)
            .setParameter("id", artisanId).setParameter("active", MemberStatus.ACTIVE)
            .setParameter("artisanRole", MemberRole.ARTISAN).setParameter("approved", APPROVED)
            .getResultStream().findFirst().map(a -> artisan(a, visibleProductCount(a.getId())));
    }

    @Override
    public Page<Map<String, Object>> subscriptions(Long memberId, Pageable pageable) {
        String joins = " FROM ArtisanSubscription s, ArtisanProfile a, Member m"
            + " WHERE s.artisanId=a.id AND m.id=a.id AND s.memberId=:memberId"
            + " AND m.status=:active AND m.role=:artisanRole AND a.certificationStatus=:approved";
        List<Object[]> rows = entityManager.createQuery(
                "SELECT s,a" + joins + " ORDER BY s.id DESC", Object[].class)
            .setParameter("memberId", memberId)
            .setParameter("active", MemberStatus.ACTIVE).setParameter("artisanRole", MemberRole.ARTISAN)
            .setParameter("approved", APPROVED)
            .setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize())
            .getResultList();
        long total = entityManager.createQuery("SELECT count(s)" + joins, Long.class)
            .setParameter("memberId", memberId)
            .setParameter("active", MemberStatus.ACTIVE).setParameter("artisanRole", MemberRole.ARTISAN)
            .setParameter("approved", APPROVED).getSingleResult();
        List<Map<String, Object>> items = rows.stream().map(row -> {
            ArtisanSubscription subscription = (ArtisanSubscription) row[0];
            ArtisanProfile profile = (ArtisanProfile) row[1];
            Map<String, Object> value = artisan(profile, visibleProductCount(profile.getId()));
            value.put("notificationsEnabled", subscription.isNotificationsEnabled());
            value.put("newProductCount", newProductCount(profile.getId(), subscription.getCreatedAt()));
            return value;
        }).toList();
        return new PageImpl<>(items, pageable, total);
    }

    @Override
    public boolean productVisible(Long productId) {
        return entityManager.createQuery(
                "SELECT count(p) FROM Product p, ArtisanProfile a, Member m"
                    + " WHERE p.id=:id AND p.artisanId=a.id AND m.id=a.id AND p.status IN :statuses"
                    + " AND m.status=:active AND a.certificationStatus=:approved", Long.class)
            .setParameter("id", productId).setParameter("statuses", VISIBLE_PRODUCTS)
            .setParameter("active", MemberStatus.ACTIVE).setParameter("approved", APPROVED)
            .getSingleResult() > 0;
    }

    @Override
    public boolean categoryExists(String category) {
        return entityManager.createQuery("SELECT count(c) FROM Category c WHERE c.name=:category", Long.class)
            .setParameter("category", category).getSingleResult() > 0;
    }

    private Page<Map<String, Object>> writtenReviews(Long memberId, Pageable pageable) {
        String from = " FROM ProductReview r, Member m WHERE r.writerId=m.id AND r.writerId=:memberId";
        List<Object[]> rows = entityManager.createQuery(
                "SELECT r,m" + from + " ORDER BY r.id DESC", Object[].class)
            .setParameter("memberId", memberId)
            .setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize())
            .getResultList();
        long total = entityManager.createQuery(
                "SELECT count(r) FROM ProductReview r WHERE r.writerId=:memberId", Long.class)
            .setParameter("memberId", memberId).getSingleResult();
        List<Map<String, Object>> items = rows.stream().map(row -> {
            ProductReview review = (ProductReview) row[0];
            Member writer = (Member) row[1];
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("reviewId", review.getId());
            value.put("productId", review.getProductId());
            value.put("rating", review.getRating());
            value.put("content", review.getContent());
            value.put("images", List.of());
            value.put("writerNickname", Optional.ofNullable(writer.getNickname()).orElse(writer.getName()));
            value.put("createdAt", review.getCreatedAt());
            return value;
        }).toList();
        return new PageImpl<>(items, pageable, total);
    }

    private Page<Map<String, Object>> writableReviews(Long memberId, Pageable pageable) {
        String from = " FROM MemberOrderItemView i, MemberOrderView o, Product p"
            + " WHERE i.orderId=o.id AND i.productId=p.id AND o.memberId=:memberId"
            + " AND o.status='DELIVERED'"
            + " AND NOT EXISTS (SELECT r.id FROM ProductReview r WHERE r.orderItemId=i.id)";
        List<Object[]> rows = entityManager.createQuery(
                "SELECT i,p" + from + " ORDER BY i.id DESC", Object[].class)
            .setParameter("memberId", memberId)
            .setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize())
            .getResultList();
        long total = entityManager.createQuery("SELECT count(i)" + from, Long.class)
            .setParameter("memberId", memberId).getSingleResult();
        List<Map<String, Object>> items = rows.stream().map(row -> {
            MemberOrderItemView item = (MemberOrderItemView) row[0];
            Product product = (Product) row[1];
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("orderItemId", item.getId());
            value.put("productId", item.getProductId());
            value.put("productName", item.getProductName());
            value.put("thumbnail", thumbnail(product.getThumbnailUrl()));
            return value;
        }).toList();
        return new PageImpl<>(items, pageable, total);
    }

    private Map<String, Object> product(Product product, ArtisanProfile artisan) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("productId", product.getId());
        value.put("name", product.getTitle());
        value.put("price", product.getPrice());
        value.put("thumbnail", thumbnail(product.getThumbnailUrl()));
        value.put("status", product.getStatus().name());
        value.put("category", product.getCategory() == null ? null : product.getCategory().getName());
        value.put("subcategory", product.getSubcategory() == null ? null : product.getSubcategory().getName());
        value.put("material", product.getMaterial());
        value.put("rating", averageRating(product.getId()));
        value.put("isLimited", false);
        value.put("isCustomOrder", false);
        value.put("isSingleItem", product.getStock() == 1);
        boolean isNew = product.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7));
        value.put("isNew", isNew);
        value.put("hasGiftWrap", false);
        value.put("hasOptions", false);
        value.put("purposeTags", List.of());
        value.put("primaryBadge", isNew ? "NEW" : null);
        value.put("artisanId", artisan.getId());
        value.put("artisanName", artisan.getBusinessName());
        return value;
    }

    private Map<String, Object> artisan(ArtisanProfile profile, long productCount) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("artisanId", profile.getId());
        value.put("businessName", profile.getBusinessName());
        value.put("introduction", profile.getIntroduction());
        value.put("profileImageUrl", profile.getProfileImageUrl());
        value.put("certificationLevel", profile.getCertificationLevel());
        value.put("isOrganization", profile.isOrganization());
        value.put("certificationStatus", profile.getCertificationStatus());
        value.put("category", profile.getCategory());
        value.put("region", profile.getRegion());
        value.put("careerYears", profile.getCareerYears());
        value.put("productCount", productCount);
        value.put("topProducts", topProducts(profile.getId()));
        value.put("certifiedYear", profile.getCertifiedYear());
        value.put("lineage", profile.getLineage());
        value.put("quote", profile.getQuote());
        value.put("bio", profile.getBio());
        value.put("videoUrl", profile.getVideoUrl());
        value.put("careerTimeline", List.of());
        return value;
    }

    private Map<String, Object> orderSummary(MemberOrderView order) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("orderId", order.getId());
        value.put("orderNumber", order.getOrderNumber());
        value.put("status", order.getStatus());
        value.put("totalAmount", order.getTotalAmount());
        value.put("createdAt", order.getCreatedAt());
        return value;
    }

    private Map<String, Object> orderDetail(MemberOrderView order) {
        Map<String, Object> value = orderSummary(order);
        List<Map<String, Object>> items = entityManager.createQuery(
                "SELECT i FROM MemberOrderItemView i WHERE i.orderId=:orderId ORDER BY i.id", MemberOrderItemView.class)
            .setParameter("orderId", order.getId()).getResultList().stream().map(item -> {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("orderItemId", item.getId());
                line.put("productId", item.getProductId());
                line.put("productName", item.getProductName());
                line.put("price", item.getPrice());
                line.put("quantity", item.getQuantity());
                return line;
            }).toList();
        value.put("items", items);
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("addressId", order.getAddressId());
        address.put("recipientName", order.getRecipientName());
        address.put("phone", order.getRecipientPhone());
        address.put("zipCode", order.getZipCode());
        address.put("address1", order.getAddress1());
        address.put("address2", order.getAddress2());
        address.put("isDefault", false);
        value.put("address", address);
        return value;
    }

    private List<Map<String, Object>> topProducts(Long artisanId) {
        return entityManager.createQuery(
                "SELECT p FROM Product p WHERE p.artisanId=:artisanId AND p.status IN :statuses"
                    + " ORDER BY p.createdAt DESC,p.id DESC", Product.class)
            .setParameter("artisanId", artisanId).setParameter("statuses", VISIBLE_PRODUCTS)
            .setMaxResults(3).getResultList().stream().map(product -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("productId", product.getId());
                item.put("thumbnail", thumbnail(product.getThumbnailUrl()));
                return item;
            }).toList();
    }

    private long visibleProductCount(Long artisanId) {
        return entityManager.createQuery(
                "SELECT count(p) FROM Product p WHERE p.artisanId=:artisanId AND p.status IN :statuses", Long.class)
            .setParameter("artisanId", artisanId).setParameter("statuses", VISIBLE_PRODUCTS).getSingleResult();
    }

    private long newProductCount(Long artisanId, LocalDateTime subscribedAt) {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        LocalDateTime since = subscribedAt.isAfter(weekAgo) ? subscribedAt : weekAgo;
        return entityManager.createQuery(
                "SELECT count(p) FROM Product p WHERE p.artisanId=:artisanId"
                    + " AND p.status IN :statuses AND p.createdAt>=:since", Long.class)
            .setParameter("artisanId", artisanId).setParameter("statuses", VISIBLE_PRODUCTS)
            .setParameter("since", since).getSingleResult();
    }

    private Double averageRating(Long productId) {
        return entityManager.createQuery(
                "SELECT avg(r.rating) FROM ProductReview r WHERE r.productId=:productId", Double.class)
            .setParameter("productId", productId).getSingleResult();
    }

    private List<Map<String, Object>> thumbnail(String url) {
        if (!hasText(url)) return List.of();
        String resolved = url.startsWith("http://") || url.startsWith("https://")
            ? url : cdn + "/" + url.replaceFirst("^/+", "");
        return List.of(Map.of("url", resolved));
    }

    private void setArtisanParameters(Query query, String certification, String category,
            InitialRange range, boolean usesProductMetric) {
        query.setParameter("active", MemberStatus.ACTIVE)
            .setParameter("artisanRole", MemberRole.ARTISAN).setParameter("approved", APPROVED);
        if (usesProductMetric) query.setParameter("statuses", VISIBLE_PRODUCTS);
        if (hasText(certification)) query.setParameter("certification", certification);
        if (hasText(category)) query.setParameter("category", category);
        if (range != null) {
            query.setParameter("initialStart", range.start());
            query.setParameter("initialEnd", range.end());
        }
    }

    private String orderStatus(String status) {
        String value = Optional.ofNullable(status).orElse("ALL");
        if (!Set.of("ALL", "CREATED", "PAID", "PAYMENT_FAILED", "CANCELED", "DELIVERED", "RETURN_REQUESTED")
            .contains(value)) throw new DomainException(ErrorCode.INVALID_INPUT);
        return value;
    }

    private SellerApplication.Status applicationStatus(String status) {
        String value = Optional.ofNullable(status).orElse("ALL");
        if ("ALL".equals(value)) return null;
        try {
            return SellerApplication.Status.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
    }

    private String artisanMetric(String sort) {
        return switch (sort) {
            case "POPULAR", "MOST_PRODUCTS", "RECENTLY_JOINED" -> sort;
            default -> throw new DomainException(ErrorCode.INVALID_INPUT);
        };
    }

    private InitialRange initialRange(String initial) {
        if (!hasText(initial)) return null;
        String initials = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
        if (initial.length() != 1 || !initials.contains(initial)) throw new DomainException(ErrorCode.INVALID_INPUT);
        int start = 0xAC00 + initials.indexOf(initial) * 588;
        return new InitialRange(String.valueOf((char) start), String.valueOf((char) (start + 588)));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record InitialRange(String start, String end) {}
}
