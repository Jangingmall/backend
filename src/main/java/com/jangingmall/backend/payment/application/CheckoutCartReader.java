package com.jangingmall.backend.payment.application;

import java.util.List;

public interface CheckoutCartReader {
    List<CartLine> selectedItems(Long memberId, List<Long> cartItemIds);

    void removePurchased(Long memberId, List<Long> cartItemIds);

    record CartLine(Long cartItemId, Long productId, int quantity, List<CheckoutCatalog.OptionSelection> selectedOptions,
                    List<CheckoutCatalog.TextInput> textInputs) {}
}
