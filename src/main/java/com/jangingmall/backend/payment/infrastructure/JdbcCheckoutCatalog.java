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
            "select product_id, name, price, stock, status, production_period_days, artisan_id, shipping_fee, "
                + "free_shipping_threshold from product where product_id = ?",
            (rs, rowNum) -> new ProductRow(rs.getLong("product_id"), rs.getString("name"), rs.getLong("price"),
                rs.getObject("stock", Integer.class), rs.getString("status"), rs.getObject("production_period_days", Integer.class),
                rs.getLong("artisan_id"), rs.getLong("shipping_fee"), rs.getObject("free_shipping_threshold", Long.class)),
            productId
        ).stream().findFirst().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (!"ON_SALE".equals(product.status()) || (product.stock() != null && product.stock() < quantity)) {
            throw new BusinessRuleViolationException("판매 중이 아니거나 재고가 부족한 상품입니다.");
        }

        Set<Long> selectedGroupIds = new HashSet<>();
        long optionPrice = 0L;
        for (OptionSelection selection : selectedOptions) {
            if (!selectedGroupIds.add(selection.optionGroupId())) {
                throw new BusinessRuleViolationException("옵션 그룹은 하나만 선택할 수 있습니다.");
            }
            if (!exists("select count(*) from product_option_group where product_id = ? and option_group_id = ?",
                productId, selection.optionGroupId())
                || !exists("select count(*) from product_option_choice where choice_id = ?", selection.choiceId())) {
                throw new DomainException(ErrorCode.NOT_FOUND);
            }
            OptionRow option = jdbcTemplate.query(
                "select g.option_group_id, c.price_delta, c.stock from product_option_group g "
                    + "join product_option_choice c on c.option_group_id = g.option_group_id "
                    + "where g.product_id = ? and g.option_group_id = ? and c.choice_id = ?",
                (rs, rowNum) -> new OptionRow(rs.getLong("option_group_id"), rs.getLong("price_delta"),
                    rs.getObject("stock", Integer.class)),
                productId, selection.optionGroupId(), selection.choiceId()
            ).stream().findFirst().orElseThrow(() -> new BusinessRuleViolationException("유효하지 않은 상품 옵션입니다."));
            if (option.stock() != null && option.stock() < quantity) {
                throw new BusinessRuleViolationException("선택한 옵션의 재고가 부족합니다.");
            }
            optionPrice = Math.addExact(optionPrice, option.priceDelta());
        }

        Set<Long> requiredGroups = new HashSet<>(jdbcTemplate.queryForList(
            "select option_group_id from product_option_group where product_id = ? and type = 'REQUIRED'", Long.class, productId));
        if (!selectedGroupIds.containsAll(requiredGroups)) {
            throw new BusinessRuleViolationException("필수 옵션을 모두 선택해야 합니다.");
        }
        validateTextInputs(productId, textInputs);
        return new ProductQuote(product.id(), product.name(), Math.addExact(product.price(), optionPrice),
            product.productionPeriodDays(), product.artisanId(), product.shippingFee(), product.freeShippingThreshold());
    }

    @Override
    public CartProductView describe(Long productId, int quantity, List<OptionSelection> selectedOptions,
                                    List<TextInput> textInputs) {
        CartProductRow product = jdbcTemplate.query(
            "select p.product_id, p.name, p.price, p.stock, p.status, p.is_custom_order, p.artisan_id, "
                + "p.shipping_fee, p.free_shipping_threshold, a.business_name, a.certification_level "
                + "from product p join artisan_profile a on a.artisan_id = p.artisan_id where p.product_id = ?",
            (rs, rowNum) -> new CartProductRow(rs.getLong("product_id"), rs.getString("name"), rs.getLong("price"),
                rs.getObject("stock", Integer.class), rs.getString("status"), rs.getBoolean("is_custom_order"),
                rs.getLong("artisan_id"), rs.getLong("shipping_fee"),
                rs.getObject("free_shipping_threshold", Long.class), rs.getString("business_name"),
                rs.getString("certification_level")), productId
        ).stream().findFirst().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));

        boolean soldOut = !"ON_SALE".equals(product.status())
            || (product.stock() != null && product.stock() < quantity);
        long unitPrice = product.price();
        List<SelectedOptionView> optionViews = new ArrayList<>();
        for (OptionSelection selection : selectedOptions) {
            CartOptionRow option = jdbcTemplate.query(
                "select g.option_group_id, g.name group_name, c.choice_id, c.name choice_name, c.price_delta, c.stock "
                    + "from product_option_group g join product_option_choice c on c.option_group_id = g.option_group_id "
                    + "where g.product_id = ? and g.option_group_id = ? and c.choice_id = ?",
                (rs, rowNum) -> new CartOptionRow(rs.getLong("option_group_id"), rs.getString("group_name"),
                    rs.getLong("choice_id"), rs.getString("choice_name"), rs.getLong("price_delta"),
                    rs.getObject("stock", Integer.class)), productId, selection.optionGroupId(), selection.choiceId()
            ).stream().findFirst().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
            unitPrice = Math.addExact(unitPrice, option.priceDelta());
            soldOut = soldOut || (option.stock() != null && option.stock() < quantity);
            optionViews.add(new SelectedOptionView(option.groupId(), option.groupName(), option.choiceId(),
                option.choiceName(), option.priceDelta()));
        }
        List<TextInputView> textViews = new ArrayList<>();
        for (TextInput input : textInputs) {
            String groupName = jdbcTemplate.query(
                "select name from product_option_group where product_id = ? and option_group_id = ? and type = 'TEXT'",
                (rs, rowNum) -> rs.getString("name"), productId, input.optionGroupId()
            ).stream().findFirst().orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
            textViews.add(new TextInputView(input.optionGroupId(), groupName, input.text()));
        }
        return new CartProductView(product.id(), product.name(), unitPrice, product.artisanId(), product.artisanName(),
            product.certificationLevel(), thumbnail(productId), product.customOrder(), soldOut, product.shippingFee(),
            product.freeShippingThreshold(), List.copyOf(optionViews), List.copyOf(textViews));
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
            for (Long choiceId : line.choiceIds()) {
                int updated = jdbcTemplate.update(
                    "update product_option_choice c set stock = case when stock is null then null else stock - ? end "
                        + "where c.choice_id = ? and (c.stock is null or c.stock >= ?) "
                        + "and exists (select 1 from product_option_group g where g.option_group_id = c.option_group_id and g.product_id = ?)",
                    line.quantity(), choiceId, line.quantity(), line.productId()
                );
                requireReserved(updated);
            }
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
            for (Long choiceId : line.choiceIds()) {
                jdbcTemplate.update(
                    "update product_option_choice c set stock = case when stock is null then null else stock + ? end "
                        + "where c.choice_id = ? and exists (select 1 from product_option_group g "
                        + "where g.option_group_id = c.option_group_id and g.product_id = ?)",
                    line.quantity(), choiceId, line.productId()
                );
            }
        }
    }

    @Override
    public void changeSalesCount(List<InventoryLine> lines, int direction) {
        if (direction != 1 && direction != -1) {
            throw new IllegalArgumentException("판매량 변경 방향은 1 또는 -1이어야 합니다.");
        }
        for (InventoryLine line : lines) {
            jdbcTemplate.update(
                "update product set sales_count = greatest(0, sales_count + ?), updated_at = current_timestamp where product_id = ?",
                Math.multiplyExact(direction, line.quantity()), line.productId()
            );
        }
    }

    private void requireReserved(int updated) {
        if (updated != 1) {
            throw new BusinessRuleViolationException("주문 가능한 재고가 부족합니다.");
        }
    }

    private boolean exists(String sql, Object... arguments) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return count != null && count > 0;
    }

    private void validateTextInputs(Long productId, List<TextInput> textInputs) {
        Set<Long> seen = new HashSet<>();
        for (TextInput input : textInputs) {
            if (input.text() == null || input.text().isBlank() || !seen.add(input.optionGroupId())) {
                throw new BusinessRuleViolationException("입력형 옵션 값이 올바르지 않습니다.");
            }
            Integer maxLength = jdbcTemplate.query(
                "select max_length from product_option_group where product_id = ? and option_group_id = ? and type = 'TEXT'",
                (rs, rowNum) -> rs.getObject("max_length", Integer.class), productId, input.optionGroupId()
            ).stream().findFirst().orElseThrow(() -> new BusinessRuleViolationException("유효하지 않은 입력형 옵션입니다."));
            if (maxLength != null && input.text().length() > maxLength) {
                throw new BusinessRuleViolationException("입력형 옵션의 글자 수를 초과했습니다.");
            }
        }
        Set<Long> requiredTextGroups = new HashSet<>(jdbcTemplate.queryForList(
            "select option_group_id from product_option_group where product_id = ? and type = 'TEXT'", Long.class, productId));
        requireAllTextInputs(seen, requiredTextGroups);
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

    private record ProductRow(Long id, String name, long price, Integer stock, String status, Integer productionPeriodDays,
                              Long artisanId, long shippingFee, Long freeShippingThreshold) {}
    private record OptionRow(Long groupId, long priceDelta, Integer stock) {}
    private record CartProductRow(Long id, String name, long price, Integer stock, String status, boolean customOrder,
                                  Long artisanId, long shippingFee, Long freeShippingThreshold, String artisanName,
                                  String certificationLevel) {}
    private record CartOptionRow(Long groupId, String groupName, Long choiceId, String choiceName, long priceDelta,
                                 Integer stock) {}
    private record ImageRow(int sourceWidth, int sourceHeight, String variants) {}
}
