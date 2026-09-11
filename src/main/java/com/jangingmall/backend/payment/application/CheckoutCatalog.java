package com.jangingmall.backend.payment.application;

import java.util.List;

public interface CheckoutCatalog {
    ProductQuote quote(Long productId, int quantity, List<OptionSelection> selectedOptions, List<TextInput> textInputs);

    /** 장바구니 표시용 상품·장인·옵션 스냅샷을 현재 데이터 기준으로 구성한다. */
    CartProductView describe(Long productId, int quantity, List<OptionSelection> selectedOptions, List<TextInput> textInputs);

    /**
     * 같은 멱등키의 주문 생성 요청을 PostgreSQL 트랜잭션 단위로 직렬화한다.
     */
    void lockOrderCreation(Long memberId, String idempotencyKey);

    /**
     * 주문 생성 시점에 재고를 조건부 원자 UPDATE로 예약한다.
     */
    void reserve(List<InventoryLine> lines);

    /**
     * 결제 실패 또는 전액 취소 시 예약했던 재고를 복구한다.
     */
    void release(List<InventoryLine> lines);

    /** 결제 완료/취소에 맞춰 상품 판매량 집계를 증감한다. */
    void changeSalesCount(List<InventoryLine> lines, int direction);

    record ProductQuote(Long productId, String productName, long unitPrice, Integer productionPeriodDays,
                        Long artisanId, long shippingFee, Long freeShippingThreshold) {
        public ProductQuote(Long productId, String productName, long unitPrice, Integer productionPeriodDays) {
            this(productId, productName, unitPrice, productionPeriodDays, productId, 0L, null);
        }
    }

    record OptionSelection(Long optionGroupId, Long choiceId) {}

    record TextInput(Long optionGroupId, String text) {}

    record CartProductView(Long productId, String productName, long unitPrice, Long artisanId,
                           String artisanName, String certificationLevel, List<ImageVariant> thumbnail,
                           boolean customOrder, boolean soldOut, long shippingFee, Long freeShippingThreshold,
                           List<SelectedOptionView> selectedOptions, List<TextInputView> textInputs) {}

    record ImageVariant(String url, int width, int height, String format) {}

    record SelectedOptionView(Long optionGroupId, String name, Long choiceId, String choiceName,
                              long priceDelta) {}

    record TextInputView(Long optionGroupId, String name, String text) {}

    record InventoryLine(Long productId, int quantity, List<Long> choiceIds) {
        public InventoryLine {
            choiceIds = choiceIds == null ? List.of() : List.copyOf(choiceIds);
        }
    }
}
