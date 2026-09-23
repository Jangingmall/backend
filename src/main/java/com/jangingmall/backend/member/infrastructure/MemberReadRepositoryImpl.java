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
import com.jangingmall.backend.image.application.ImageService;
import com.jangingmall.backend.product.domain.Product;
import com.jangingmall.backend.product.domain.ProductImage;
import com.jangingmall.backend.product.domain.ProductImageRepository;
import com.jangingmall.backend.product.domain.ProductReview;
import com.jangingmall.backend.product.domain.ProductStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import com.jangingmall.backend.payment.domain.OrderReturn;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

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
    private final ProductImageRepository productImages;
    private final ImageService images;
    private final ObjectMapper objectMapper;

    public MemberReadRepositoryImpl(@Value("${member.image-base-url:http://localhost:8080/media}") String cdn,
                                    ProductImageRepository productImages, ImageService images,
                                    ObjectMapper objectMapper) {
        this.cdn = cdn.replaceAll("/+$", "");
        this.productImages = productImages;
        this.images = images;
        this.objectMapper = objectMapper;
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
    public Page<Map<String, Object>> orders(Long memberId, Pageable pageable, String status,
            String from, String to, String artisanName) {
        Set<String> statuses = orderStatuses(status);
        StringBuilder where = new StringBuilder("WHERE o.memberId=:memberId"
            + " AND o.status<>'PAYMENT_FAILED'"
            + " AND (o.status<>'RETURN_REQUESTED'"
            + " OR EXISTS (SELECT r.id FROM OrderReturn r WHERE r.orderId=o.id))");
        if (!statuses.contains("ALL")) where.append(" AND o.status IN :statuses");
        LocalDateTime fromDt = parseDateStart(from);
        LocalDateTime toDt = parseDateEnd(to);
        if (fromDt != null) where.append(" AND o.createdAt>=:from");
        if (toDt != null) where.append(" AND o.createdAt<:to");
        if (hasText(artisanName)) where.append(
            " AND EXISTS (SELECT 1 FROM MemberOrderItemView i, Product p, ArtisanProfile a"
                + " WHERE i.orderId=o.id AND p.id=i.productId AND a.id=p.artisanId"
                + " AND a.businessName LIKE :artisanName)");

        var query = entityManager.createQuery(
                "SELECT o FROM MemberOrderView o " + where + " ORDER BY o.id DESC", MemberOrderView.class)
            .setParameter("memberId", memberId)
            .setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
        var count = entityManager.createQuery(
                "SELECT count(o) FROM MemberOrderView o " + where, Long.class)
            .setParameter("memberId", memberId);
        applyOrderFilters(query, statuses, fromDt, toDt, artisanName);
        applyOrderFilters(count, statuses, fromDt, toDt, artisanName);

        List<MemberOrderView> orderViews = query.getResultList();
        List<Long> orderIds = orderViews.stream().map(MemberOrderView::getId).toList();
        Map<Long, List<Map<String, Object>>> itemsByOrder = orderIds.isEmpty()
            ? Map.of() : fetchOrderItems(orderIds);
        Map<Long, OrderReturn> returnByOrder = orderIds.isEmpty()
            ? Map.of() : fetchOrderReturns(orderIds);

        List<Map<String, Object>> items = orderViews.stream()
            .map(o -> orderListEntry(o, itemsByOrder.getOrDefault(o.getId(), List.of()),
                returnByOrder.get(o.getId())))
            .toList();
        return new PageImpl<>(items, pageable, count.getSingleResult());
    }

    @Override
    public Optional<Map<String, Object>> order(Long memberId, Long orderId) {
        return entityManager.createQuery(
                "SELECT o FROM MemberOrderView o WHERE o.id=:id AND o.memberId=:memberId", MemberOrderView.class)
            .setParameter("id", orderId).setParameter("memberId", memberId).getResultStream().findFirst()
            .map(o -> {
                List<Map<String, Object>> items = fetchOrderItems(List.of(o.getId()))
                    .getOrDefault(o.getId(), List.of());
                OrderReturn orderReturn = fetchOrderReturns(List.of(o.getId())).get(o.getId());
                return orderDetail(o, items, orderReturn);
            });
    }

    @Override
    public Map<String, Object> orderSummary(Long memberId) {
        LocalDateTime since = LocalDateTime.now().minusMonths(3);
        List<Object[]> rows = entityManager.createQuery(
                "SELECT o.status, count(o) FROM MemberOrderView o"
                    + " WHERE o.memberId=:memberId AND o.createdAt>=:since GROUP BY o.status",
                Object[].class)
            .setParameter("memberId", memberId).setParameter("since", since).getResultList();
        Map<String, Long> counts = rows.stream()
            .collect(Collectors.toMap(r -> (String) r[0], r -> (Long) r[1]));
        Map<String, Object> inProgress = new LinkedHashMap<>();
        inProgress.put("awaitingPayment", counts.getOrDefault("CREATED", 0L));
        inProgress.put("preparing", counts.getOrDefault("PAID", 0L));
        inProgress.put("inDelivery", counts.getOrDefault("IN_DELIVERY", 0L));
        inProgress.put("delivered", counts.getOrDefault("DELIVERED", 0L));
        Map<String, Object> closedCount = new LinkedHashMap<>();
        closedCount.put("returnOrExchange", counts.getOrDefault("RETURN_REQUESTED", 0L));
        closedCount.put("canceled", counts.getOrDefault("CANCELED", 0L));
        closedCount.put("purchaseConfirmed", counts.getOrDefault("PURCHASE_CONFIRMED", 0L));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inProgress", inProgress);
        result.put("closedCount", closedCount);
        return result;
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
        String from = " FROM ProductReview r, Member m, MemberOrderItemView i, Product p"
            + " WHERE r.writerId=m.id AND r.orderItemId=i.id AND i.productId=p.id AND r.writerId=:memberId";
        List<Object[]> rows = entityManager.createQuery(
                "SELECT r,m,i,p" + from + " ORDER BY r.id DESC", Object[].class)
            .setParameter("memberId", memberId)
            .setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize())
            .getResultList();
        long total = entityManager.createQuery(
                "SELECT count(r) FROM ProductReview r WHERE r.writerId=:memberId", Long.class)
            .setParameter("memberId", memberId).getSingleResult();
        Map<Long, Map<String, Object>> thumbnailByProduct = imageRefs(rows.stream()
            .map(row -> ((Product) row[3]).getId()).toList());
        List<Map<String, Object>> items = rows.stream().map(row -> {
            ProductReview review = (ProductReview) row[0];
            Member writer = (Member) row[1];
            MemberOrderItemView item = (MemberOrderItemView) row[2];
            Product product = (Product) row[3];
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("reviewId", review.getId());
            value.put("orderItemId", review.getOrderItemId());
            value.put("productId", review.getProductId());
            value.put("productName", item.getProductName());
            value.put("rating", review.getRating());
            value.put("content", review.getContent());
            putThumbnail(value, thumbnailByProduct.get(product.getId()), product.getThumbnailUrl());
            value.put("images", reviewImages(review.getImages()));
            value.put("writerNickname", Optional.ofNullable(writer.getNickname()).orElse(writer.getName()));
            value.put("createdAt", review.getCreatedAt());
            return value;
        }).toList();
        return new PageImpl<>(items, pageable, total);
    }

    private Page<Map<String, Object>> writableReviews(Long memberId, Pageable pageable) {
        String from = " FROM MemberOrderItemView i, MemberOrderView o, Product p"
            + " WHERE i.orderId=o.id AND i.productId=p.id AND o.memberId=:memberId"
            + " AND o.status IN ('DELIVERED','PURCHASE_CONFIRMED')"
            + " AND NOT EXISTS (SELECT r.id FROM ProductReview r WHERE r.orderItemId=i.id)";
        List<Object[]> rows = entityManager.createQuery(
                "SELECT i,o,p" + from + " ORDER BY i.id DESC", Object[].class)
            .setParameter("memberId", memberId)
            .setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize())
            .getResultList();
        long total = entityManager.createQuery("SELECT count(i)" + from, Long.class)
            .setParameter("memberId", memberId).getSingleResult();
        Map<Long, Map<String, Object>> thumbnailByProduct = imageRefs(rows.stream()
            .map(row -> ((Product) row[2]).getId()).toList());
        List<Map<String, Object>> items = rows.stream().map(row -> {
            MemberOrderItemView item = (MemberOrderItemView) row[0];
            MemberOrderView order = (MemberOrderView) row[1];
            Product product = (Product) row[2];
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("orderItemId", item.getId());
            value.put("productId", item.getProductId());
            value.put("productName", item.getProductName());
            value.put("purchasedAt", order.getCreatedAt());
            value.put("options", options(item.getSelectedOptionsSnapshot()));
            putThumbnail(value, thumbnailByProduct.get(product.getId()), product.getThumbnailUrl());
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

    private Map<String, Object> orderBase(MemberOrderView order) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("orderId", order.getId());
        value.put("orderNumber", order.getOrderNumber());
        value.put("status", order.getStatus());
        value.put("totalAmount", order.getTotalAmount());
        value.put("createdAt", order.getCreatedAt());
        return value;
    }

    private Map<String, Object> orderListEntry(MemberOrderView order, List<Map<String, Object>> items,
            OrderReturn orderReturn) {
        Map<String, Object> value = orderBase(order);
        value.put("items", items);
        if (orderReturn != null) {
            Map<String, Object> returnInfo = new LinkedHashMap<>();
            returnInfo.put("type", orderReturn.getType().name());
            returnInfo.put("status", orderReturn.getStatus().name());
            value.put("returnInfo", returnInfo);
        }
        return value;
    }

    private Map<String, Object> orderDetail(MemberOrderView order, List<Map<String, Object>> items,
            OrderReturn orderReturn) {
        Map<String, Object> value = orderBase(order);
        value.put("paymentMethod", order.getPaymentMethod());
        value.put("shippingAmount", order.getShippingAmount());
        value.put("items", items);
        if (orderReturn != null) {
            Map<String, Object> returnInfo = new LinkedHashMap<>();
            returnInfo.put("type", orderReturn.getType().name());
            returnInfo.put("status", orderReturn.getStatus().name());
            value.put("returnInfo", returnInfo);
        }
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

    private Map<Long, List<Map<String, Object>>> fetchOrderItems(List<Long> orderIds) {
        List<Object[]> rows = entityManager.createQuery(
                "SELECT i, p FROM MemberOrderItemView i, Product p"
                    + " WHERE i.productId=p.id AND i.orderId IN :ids ORDER BY i.id",
                Object[].class)
            .setParameter("ids", orderIds).getResultList();
        List<Long> productIds = rows.stream().map(row -> ((Product) row[1]).getId()).toList();
        Map<Long, Map<String, Object>> thumbnailByProduct = imageRefs(productIds);
        Map<Long, ArtisanProfile> artisanById = artisanProfiles(rows.stream()
            .map(row -> ((Product) row[1]).getArtisanId()).toList());
        Map<Long, Long> reviewByOrderItem = reviewIds(rows.stream()
            .map(row -> ((MemberOrderItemView) row[0]).getId()).toList());
        return rows.stream().collect(Collectors.groupingBy(
            row -> ((MemberOrderItemView) row[0]).getOrderId(),
            Collectors.mapping(row -> {
                MemberOrderItemView item = (MemberOrderItemView) row[0];
                Product product = (Product) row[1];
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("orderItemId", item.getId());
                line.put("productId", item.getProductId());
                line.put("productName", item.getProductName());
                line.put("price", item.getPrice());
                line.put("quantity", item.getQuantity());
                line.put("options", options(item.getSelectedOptionsSnapshot()));
                ArtisanProfile artisan = artisanById.get(product.getArtisanId());
                Map<String, Object> artisanValue = new LinkedHashMap<>();
                artisanValue.put("artisanId", product.getArtisanId());
                artisanValue.put("businessName", artisan == null ? null : artisan.getBusinessName());
                line.put("artisan", artisanValue);
                line.put("reviewId", reviewByOrderItem.get(item.getId()));
                putThumbnail(line, thumbnailByProduct.get(product.getId()), product.getThumbnailUrl());
                return line;
            }, Collectors.toList())
        ));
    }

    private Map<Long, OrderReturn> fetchOrderReturns(List<Long> orderIds) {
        return entityManager.createQuery(
                "SELECT r FROM OrderReturn r WHERE r.orderId IN :ids", OrderReturn.class)
            .setParameter("ids", orderIds).getResultList().stream()
            .collect(Collectors.toMap(OrderReturn::getOrderId, r -> r));
    }

    private void applyOrderFilters(jakarta.persistence.Query query, Set<String> statuses,
            LocalDateTime from, LocalDateTime to, String artisanName) {
        if (!statuses.contains("ALL")) query.setParameter("statuses", statuses);
        if (from != null) query.setParameter("from", from);
        if (to != null) query.setParameter("to", to);
        if (hasText(artisanName)) query.setParameter("artisanName", "%" + artisanName + "%");
    }

    private LocalDateTime parseDateStart(String date) {
        if (!hasText(date)) return null;
        try {
            return LocalDate.parse(date).atStartOfDay();
        } catch (Exception e) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
    }

    private LocalDateTime parseDateEnd(String date) {
        if (!hasText(date)) return null;
        try {
            return LocalDate.parse(date).plusDays(1).atStartOfDay();
        } catch (Exception e) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
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
        return List.of(Map.of("url", legacyUrl(url)));
    }

    /**
     * New order/review contract: a product image is an image aggregate with its three public WebP variants.
     * legacyThumbnailUrl remains available only while old thumbnail_url-only products are migrated.
     */
    private Map<Long, Map<String, Object>> imageRefs(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (ProductImage image : productImages.findByProductIdInOrderByProductIdAscDisplayOrderAsc(
            productIds.stream().distinct().toList())) {
            if (result.containsKey(image.getProductId())) {
                continue;
            }
            result.put(image.getProductId(), imageRef(image.getImageId()));
        }
        return result;
    }

    private Map<String, Object> imageRef(String imageId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imageId", imageId);
        result.put("variants", images.publicVariants(imageId));
        return result;
    }

    private void putThumbnail(Map<String, Object> target, Map<String, Object> imageRef, String legacyThumbnailUrl) {
        target.put("thumbnail", imageRef);
        if (imageRef == null && hasText(legacyThumbnailUrl)) {
            target.put("legacyThumbnailUrl", legacyUrl(legacyThumbnailUrl));
        }
    }

    private String legacyUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://")
            ? url : cdn + "/" + url.replaceFirst("^/+", "");
    }

    private Map<Long, ArtisanProfile> artisanProfiles(List<Long> artisanIds) {
        if (artisanIds == null || artisanIds.isEmpty()) {
            return Map.of();
        }
        return entityManager.createQuery("SELECT a FROM ArtisanProfile a WHERE a.id IN :ids", ArtisanProfile.class)
            .setParameter("ids", artisanIds.stream().distinct().toList()).getResultList().stream()
            .collect(Collectors.toMap(ArtisanProfile::getId, artisan -> artisan));
    }

    private Map<Long, Long> reviewIds(List<Long> orderItemIds) {
        if (orderItemIds == null || orderItemIds.isEmpty()) {
            return Map.of();
        }
        return entityManager.createQuery(
                "SELECT r.orderItemId,r.id FROM ProductReview r WHERE r.orderItemId IN :ids", Object[].class)
            .setParameter("ids", orderItemIds.stream().distinct().toList()).getResultList().stream()
            .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> options(String snapshot) {
        Map<String, Object> empty = Map.of("selectedOptions", List.of(), "textInputs", List.of());
        if (!hasText(snapshot)) {
            return empty;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(snapshot, Map.class);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("selectedOptions", parsed.getOrDefault("selectedOptions", List.of()));
            result.put("textInputs", parsed.getOrDefault("textInputs", List.of()));
            return result;
        } catch (Exception exception) {
            return empty;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> reviewImages(String storedImages) {
        if (!hasText(storedImages)) {
            return List.of();
        }
        try {
            List<Object> values = objectMapper.readValue(storedImages, List.class);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object value : values) {
                if (value == null || String.valueOf(value).isBlank()) {
                    continue;
                }
                String reference = String.valueOf(value);
                if (reference.startsWith("http://") || reference.startsWith("https://")) {
                    result.add(Map.of("legacyImageUrl", reference));
                } else {
                    result.add(imageRef(reference));
                }
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            return List.of();
        }
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

    private Set<String> orderStatuses(String status) {
        String supplied = Optional.ofNullable(status).filter(this::hasText).orElse("ALL");
        Set<String> values = Arrays.stream(supplied.split(","))
            .map(String::trim).filter(this::hasText)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> allowed = Set.of("ALL", "CREATED", "PAID", "PAYMENT_FAILED", "CANCELED",
            "IN_DELIVERY", "DELIVERED", "PURCHASE_CONFIRMED", "RETURN_REQUESTED");
        if (values.isEmpty() || values.stream().anyMatch(value -> !allowed.contains(value))
            || (values.contains("ALL") && values.size() != 1)) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        return values;
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
