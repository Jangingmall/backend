package com.jangingmall.backend.member.infrastructure;

import static com.jangingmall.backend.member.infrastructure.MemberReadSql.*;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.application.*;
import com.jangingmall.backend.member.domain.SellerApplication;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class MemberReadRepositoryImpl implements MemberReadRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String cdn;
    @PersistenceContext private EntityManager entityManager;

    public MemberReadRepositoryImpl(DataSource dataSource, ObjectMapper json,
                                    @Value("${image.base-url:}") String cdn) {
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.json = json;
        this.cdn = cdn.replaceAll("/+$", "");
    }

    @Override
    public CursorPage<SellerApplicationData> applications(PageRequest page, String status) {
        String filter = applicationStatus(status);
        var query = entityManager.createQuery("SELECT a FROM SellerApplication a WHERE a.id < :id "
            + "AND (:status = 'ALL' OR cast(a.status as string) = :status) ORDER BY a.id DESC", SellerApplication.class);
        var rows = query.setParameter("id", page.beforeId()).setParameter("status", filter).setMaxResults(page.limit()+1).getResultList();
        long total = entityManager.createQuery("SELECT count(a) FROM SellerApplication a WHERE :status='ALL' OR cast(a.status as string)=:status", Long.class)
            .setParameter("status", filter).getSingleResult();
        boolean more = rows.size() > page.limit();
        var items = rows.stream().limit(page.limit()).map(SellerApplicationData::from).toList();
        return new CursorPage<>(items, more ? PageRequest.encode(items.getLast().applicationId()) : null, more, total);
    }

    @Override
    public CursorPage<Map<String, Object>> wishes(Long memberId, PageRequest page) {
        String from = "wishlist w JOIN " + PRODUCT_FROM + " ON p.product_id=w.product_id";
        // The product join is written explicitly to keep every ON clause attached to its join.
        from = "wishlist w JOIN product p ON p.product_id=w.product_id JOIN artisan_profile a ON a.artisan_id=p.artisan_id JOIN member m ON m.member_id=a.artisan_id";
        return page(PRODUCT, from, "w.member_id=:memberId AND " + VISIBLE_PRODUCT, "w.wishlist_id", page, memberParameters(memberId));
    }

    @Override
    public CursorPage<Map<String, Object>> orders(Long memberId, PageRequest page, String status) {
        var parameters = memberParameters(memberId);
        parameters.put("status", orderStatus(status));
        return page(ORDER, "orders o", "o.member_id=:memberId AND (:status='ALL' OR o.status=:status)", "o.order_id", page, parameters);
    }

    @Override
    public Optional<Map<String, Object>> order(Long memberId, Long orderId) {
        return one(ORDER_DETAIL, "orders o", "o.order_id=:id AND o.member_id=:memberId", Map.of("id",orderId, "memberId",memberId));
    }

    @Override
    public CursorPage<Map<String, Object>> reviews(Long memberId, PageRequest page, boolean writable) {
        if (writable) {
            return writableReviews(memberId, page);
        }
        String payload = "jsonb_build_object('reviewId',r.review_id,'productId',r.product_id,'rating',r.rating,'content',r.content,"
            + "'images',COALESCE(to_jsonb(r.images),'[]'::jsonb),'writerNickname',COALESCE(m.nickname,m.name),'createdAt',r.created_at)";
        return page(payload,"product_review r JOIN member m ON m.member_id=r.writer_id","r.writer_id=:memberId","r.review_id",page,memberParameters(memberId));
    }

    @Override
    public CursorPage<Map<String, Object>> recentViews(Long memberId, String cursor, int limit) {
        String from = "recent_view rv JOIN product p ON p.product_id=rv.product_id JOIN artisan_profile a ON a.artisan_id=p.artisan_id JOIN member m ON m.member_id=a.artisan_id";
        return sortedPage(PRODUCT + " || jsonb_build_object('viewedAt',rv.viewed_at)", from,
            "rv.member_id=:memberId AND " + VISIBLE_PRODUCT, "rv.recent_view_id", "extract(epoch FROM rv.viewed_at)",
            cursor, limit, "recent-" + memberId, memberParameters(memberId));
    }

    @Override
    public CursorPage<Map<String, Object>> artisans(String cursor, int limit, String certification, String category, String initial, String sort) {
        var parameters = new HashMap<String, Object>();
        parameters.put("certification", Optional.ofNullable(certification).orElse(""));
        parameters.put("category", Optional.ofNullable(category).orElse(""));
        parameters.put("initial", initialPattern(initial));
        String where = VISIBLE_ARTISAN + " AND (:certification='' OR a.certification_level=:certification)"
            + " AND (:category='' OR a.category_code=:category) AND a.business_name ~ :initial";
        String metric = artisanMetric(sort);
        return sortedPage(ARTISAN, ARTISAN_FROM, where, "a.artisan_id", metric, cursor, limit, sort, parameters);
    }

    @Override
    public Optional<Map<String, Object>> artisan(Long artisanId) {
        return one(ARTISAN, ARTISAN_FROM, VISIBLE_ARTISAN + " AND a.artisan_id=:id", Map.of("id",artisanId));
    }

    @Override
    public CursorPage<Map<String, Object>> subscriptions(Long memberId, PageRequest page) {
        String payload = ARTISAN + " || jsonb_build_object('notificationsEnabled',s.notifications_enabled,'newProductCount',"
            + "(SELECT count(*) FROM product p WHERE p.artisan_id=a.artisan_id AND p.status='ON_SALE' "
            + "AND p.created_at >= GREATEST(s.created_at,CURRENT_TIMESTAMP - INTERVAL '7 days')))";
        return page(payload, "artisan_subscription s JOIN artisan_profile a ON a.artisan_id=s.artisan_id JOIN member m ON m.member_id=a.artisan_id",
            "s.member_id=:memberId AND " + VISIBLE_ARTISAN, "s.subscription_id",page,memberParameters(memberId));
    }

    @Override
    public boolean productVisible(Long productId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM " + PRODUCT_FROM + " WHERE p.product_id=:id AND " + VISIBLE_PRODUCT + ")",Map.of("id",productId),Boolean.class));
    }

    @Override
    public boolean categoryExists(String category) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM category WHERE category_code=:category)",Map.of("category",category),Boolean.class));
    }

    private CursorPage<Map<String, Object>> writableReviews(Long memberId, PageRequest page) {
        String payload = "jsonb_build_object('orderItemId',i.order_item_id,'productId',i.product_id,'productName',i.product_name_snapshot,'thumbnail'," + IMAGE + ")";
        return page(payload, "order_item i JOIN orders o ON o.order_id=i.order_id JOIN product p ON p.product_id=i.product_id",
            "o.member_id=:memberId AND o.status='DELIVERED' AND NOT EXISTS(SELECT 1 FROM product_review r WHERE r.order_item_id=i.order_item_id)",
            "i.order_item_id",page,memberParameters(memberId));
    }

    private CursorPage<Map<String, Object>> page(String payload, String from, String where, String key, PageRequest page, Map<String,Object> parameters) {
        parameters.put("before",page.beforeId());
        parameters.put("limit",page.limit()+1);
        parameters.put("cdn",cdn);
        var rows = jdbc.query("SELECT " + key + " AS row_id, " + payload + " AS payload FROM " + from + " WHERE " + where
            + " AND " + key + " < :before ORDER BY " + key + " DESC LIMIT :limit",parameters,
            (result,index) -> new ProjectionRow(result.getLong("row_id"),BigDecimal.ZERO,decode(result.getString("payload"))));
        boolean more = rows.size()>page.limit();
        var kept = rows.stream().limit(page.limit()).toList();
        return new CursorPage<>(kept.stream().map(ProjectionRow::payload).toList(),
            more ? PageRequest.encode(kept.getLast().id()) : null,more,total(from,where,parameters));
    }

    private CursorPage<Map<String, Object>> sortedPage(String payload, String from, String where, String key, String metric,
            String cursor, int limit, String scope, Map<String,Object> parameters) {
        ProjectionCursor after = ProjectionCursor.parse(cursor,scope,limit);
        parameters.putAll(Map.of("value",after.value(),"before",after.id(),"limit",limit+1,"cdn",cdn));
        String sql = "SELECT " + key + " AS row_id," + metric + " AS sort_value," + payload + " AS payload FROM " + from
            + " WHERE " + where + " AND (" + metric + "," + key + ") < (:value,:before) ORDER BY " + metric + " DESC," + key + " DESC LIMIT :limit";
        var rows = jdbc.query(sql,parameters,(result,index) -> new ProjectionRow(result.getLong("row_id"),
            result.getBigDecimal("sort_value"),decode(result.getString("payload"))));
        boolean more = rows.size()>limit;
        var kept = rows.stream().limit(limit).toList();
        return new CursorPage<>(kept.stream().map(ProjectionRow::payload).toList(),more
            ? new ProjectionCursor(kept.getLast().value(),kept.getLast().id()).encode(scope) : null,more,total(from,where,parameters));
    }

    private Optional<Map<String,Object>> one(String payload, String from, String where, Map<String,Object> values) {
        var parameters = new HashMap<>(values);
        parameters.put("cdn",cdn);
        return jdbc.query("SELECT " + payload + " AS payload FROM " + from + " WHERE " + where,parameters,
            (result,index) -> decode(result.getString("payload"))).stream().findFirst();
    }

    private long total(String from, String where, Map<String,Object> parameters) {
        return jdbc.queryForObject("SELECT count(*) FROM " + from + " WHERE " + where,parameters,Long.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> decode(String value) {
        return json.readValue(value,LinkedHashMap.class);
    }

    private Map<String,Object> memberParameters(Long memberId) {
        return new HashMap<>(Map.of("memberId",memberId));
    }

    private String orderStatus(String status) {
        String value = Optional.ofNullable(status).orElse("ALL");
        if (!Set.of("ALL","CREATED","PAID","PAYMENT_FAILED","CANCELED","DELIVERED","RETURN_REQUESTED").contains(value)) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        return value;
    }

    private String applicationStatus(String status) {
        String value = Optional.ofNullable(status).orElse("ALL");
        if (!Set.of("ALL","PENDING","APPROVED","REJECTED").contains(value)) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        return value;
    }

    private String artisanMetric(String sort) {
        return switch (sort) {
            case "POPULAR" -> "a.popularity_score";
            case "MOST_PRODUCTS" -> PRODUCT_COUNT;
            case "RECENTLY_JOINED" -> "extract(epoch FROM m.created_at)";
            default -> throw new DomainException(ErrorCode.INVALID_INPUT);
        };
    }

    private String initialPattern(String initial) {
        if (initial == null || initial.isBlank()) {
            return "^";
        }
        String initials = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
        if (initial.length()!=1 || !initials.contains(initial)) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        int start = 0xAC00 + initials.indexOf(initial)*588;
        return "^[" + (char)start + "-" + (char)(start+587) + "]";
    }

    private record ProjectionRow(long id, BigDecimal value, Map<String,Object> payload) {}
}
