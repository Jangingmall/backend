package com.jangingmall.backend.payment.infrastructure;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.payment.application.CheckoutCatalog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.ObjectMapper;

/**
 * 상품 도메인의 물리 테이블만 읽는 결제용 조회 어댑터다. 상품 JPA 엔티티와 연관관계를 만들지 않아
 * 가격·판매 상태·옵션 검증의 책임 경계를 보존한다.
 */
@Component
@RequiredArgsConstructor
public class JdbcCheckoutCatalog implements CheckoutCatalog {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${image.base-url:}")
    private String imageBaseUrl;

    @Override
    public ProductQuote quote(Long productId, int quantity, List<OptionSelection> selectedOptions, List<TextInput> textInputs) {
        if (quantity < 1) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        ProductRow product = jdbcTemplate.query(
            "select product_id, title, price, stock, status, production_period_days, artisan_id from product where product_id = ?",
            (rs, rowNum) -> new ProductRow(rs.getLong("product_id"), rs.getString("title"), rs.getLong("price"),
                rs.getObject("stock", Integer.class), rs.getString("status"), rs.getObject("production_period_days", Integer.class),
                rs.getLong("artisan_id")),
            productId
        ).stream().findFirst().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (!"ON_SALE".equals(product.status()) || (product.stock() != null && product.stock() < quantity)) {
            throw new BusinessRuleViolationException("판매 중이 아니거나 재고가 부족한 상품입니다.");
        }

        return new ProductQuote(product.id(), product.name(), product.price(),
            product.productionPeriodDays(), product.artisanId(), 0L, null);
    }

    @Override
    public CartProductView describe(Long productId, int quantity, List<OptionSelection> selectedOptions,
                                    List<TextInput> textInputs) {
        CartProductRow product = jdbcTemplate.query(
            "select p.product_id, p.title, p.price, p.stock, p.status, p.is_custom_order, p.artisan_id "
                + "from product p where p.product_id = ?",
            (rs, rowNum) -> new CartProductRow(rs.getLong("product_id"), rs.getString("title"), rs.getLong("price"),
                rs.getObject("stock", Integer.class), rs.getString("status"), rs.getBoolean("is_custom_order"),
                rs.getLong("artisan_id")),
            productId
        ).stream().findFirst().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));

        String artisanName = null;
        String certificationLevel = null;
        List<Map<String, Object>> artisanRows = jdbcTemplate.queryForList(
            "select business_name, certification_level from artisan_profile where artisan_id = ?",
            product.artisanId()
        );
        if (!artisanRows.isEmpty()) {
            artisanName = (String) artisanRows.get(0).get("business_name");
            certificationLevel = (String) artisanRows.get(0).get("certification_level");
        }

        boolean soldOut = !"ON_SALE".equals(product.status())
            || (product.stock() != null && product.stock() < quantity);

        return new CartProductView(product.id(), product.name(), product.price(), product.artisanId(), artisanName,
            certificationLevel, thumbnail(productId), product.customOrder(), soldOut, 0L,
            null, List.of(), List.of());
    }

    @Override
    public void lockOrderCreation(Long memberId, String idempotencyKey) {
        if (idempotencyKey != null) {
            jdbcTemplate.query(
                "select pg_advisory_xact_lock(hashtextextended(?, 0))",
                (org.springframework.jdbc.core.RowCallbackHandler) resultSet -> { },
                memberId + ":" + idempotencyKey
            );
        }
    }

    @Override
    public void reserve(List<InventoryLine> lines) {
        for (InventoryLine line : lines) {
            int updatedProduct = jdbcTemplate.update(
                "update product set stock = case when stock is null then null else stock - ? end, "
                    + "status = case when stock is not null and stock - ? = 0 then 'SOLD_OUT' else status end, "
                    + "updated_at = current_timestamp "
                    + "where product_id = ? and status = 'ON_SALE' and (stock is null or stock >= ?)",
                line.quantity(), line.quantity(), line.productId(), line.quantity()
            );
            requireReserved(updatedProduct);
        }
    }

    @Override
    public void release(List<InventoryLine> lines) {
        for (InventoryLine line : lines) {
            jdbcTemplate.update(
                "update product set stock = case when stock is null then null else stock + ? end, "
                    + "status = case when status = 'SOLD_OUT' then 'ON_SALE' else status end, updated_at = current_timestamp "
                    + "where product_id = ?",
                line.quantity(), line.productId()
            );
        }
    }

    @Override
    public void changeSalesCount(List<InventoryLine> lines, int direction) {
        if (direction != 1 && direction != -1) {
            throw new IllegalArgumentException("판매량 변경 방향은 1 또는 -1이어야 합니다.");
        }
        // sales_count 컬럼이 스키마에 없으므로 no-op
    }

    private void requireReserved(int updated) {
        if (updated != 1) {
            throw new BusinessRuleViolationException("주문 가능한 재고가 부족합니다.");
        }
    }

    static void requireAllTextInputs(Set<Long> suppliedGroups, Set<Long> requiredGroups) {
        if (!suppliedGroups.containsAll(requiredGroups)) {
            throw new BusinessRuleViolationException("필수 입력형 옵션을 모두 입력해야 합니다.");
        }
    }

    @SuppressWarnings("unchecked")
    private List<ImageVariant> thumbnail(Long productId) {
        ImageRow image = jdbcTemplate.query(
            "select iu.source_width, iu.source_height, cast(iu.variants as text) variants "
                + "from product_image pi join image_upload iu on iu.image_id = pi.image_id "
                + "where pi.product_id = ? and pi.display_order = 0 and iu.consumed = true limit 1",
            (rs, rowNum) -> new ImageRow(rs.getInt("source_width"), rs.getInt("source_height"),
                rs.getString("variants")), productId
        ).stream().findFirst().orElse(null);
        if (image == null) return List.of();
        try {
            Map<String, Object> variants = objectMapper.readValue(image.variants(), Map.class);
            List<ImageVariant> result = new ArrayList<>();
            for (String key : List.of("320w", "640w", "1280w")) {
                Object metadata = variants.get(key);
                if (!(metadata instanceof Map<?, ?> values) || values.get("objectKey") == null) continue;
                int width = Integer.parseInt(key.substring(0, key.length() - 1));
                int height = (int) Math.round((double) width / image.sourceWidth() * image.sourceHeight());
                String objectKey = String.valueOf(values.get("objectKey"));
                String base = imageBaseUrl == null ? "" : imageBaseUrl.replaceAll("/+$", "");
                String url = base.isBlank() ? objectKey : base + "/" + objectKey.replaceAll("^/+", "");
                result.add(new ImageVariant(url, width, height, "webp"));
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private record ProductRow(Long id, String name, long price, Integer stock, String status,
                              Integer productionPeriodDays, Long artisanId) {}
    private record CartProductRow(Long id, String name, long price, Integer stock, String status,
                                  boolean customOrder, Long artisanId) {}
    private record ImageRow(int sourceWidth, int sourceHeight, String variants) {}
}
