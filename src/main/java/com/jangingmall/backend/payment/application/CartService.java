package com.jangingmall.backend.payment.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.payment.domain.Cart;
import com.jangingmall.backend.payment.domain.CartItem;
import com.jangingmall.backend.payment.domain.CartItemRepository;
import com.jangingmall.backend.payment.domain.CartRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class CartService implements CheckoutCartReader {

    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final CheckoutCatalog catalog;
    private final ObjectMapper json;

    @Transactional(readOnly = true)
    public CartData get(Long memberId, String guestCartId) {
        Cart cart = find(memberId, guestCartId);
        return cart == null ? CartData.empty() : cartData(cartItems.findByCartIdOrderByIdAsc(cart.getId()));
    }

    @Transactional
    public CartMutation add(Long memberId, String guestCartId, CartCommand command) {
        List<CheckoutCatalog.OptionSelection> options = safe(command.selectedOptions());
        List<CheckoutCatalog.TextInput> textInputs = safe(command.textInputs());
        String optionKey = optionKey(options);
        String textKey = textKey(textInputs);
        Cart existingCart = find(memberId, guestCartId);
        CartItem matching = existingCart == null ? null : cartItems.findByCartIdOrderByIdAsc(existingCart.getId()).stream()
            .filter(item -> item.getProductId().equals(command.productId())
                && item.getSelectedOptions().equals(optionKey) && item.getTextInputs().equals(textKey))
            .findFirst().orElse(null);
        int finalQuantity = matching == null ? command.quantity() : Math.addExact(matching.getQuantity(), command.quantity());
        catalog.quote(command.productId(), finalQuantity, options, textInputs);
        Cart cart = existingCart == null ? findOrCreate(memberId, guestCartId) : existingCart;
        if (matching == null) {
            matching = cartItems.save(new CartItem(cart.getId(), command.productId(), command.quantity(), optionKey, textKey));
        } else {
            matching.addQuantity(command.quantity());
        }
        cart.touch();
        return new CartMutation(cartData(cartItems.findByCartIdOrderByIdAsc(cart.getId())), cart.getGuestCartId());
    }

    @Transactional
    public CartMutation changeQuantity(Long memberId, String guestCartId, Long cartItemId, int quantity) {
        Cart cart = required(memberId, guestCartId);
        CartItem item = ownedItem(cart, cartItemId);
        catalog.quote(item.getProductId(), quantity, parseOptions(item.getSelectedOptions()), parseTextInputs(item.getTextInputs()));
        item.changeQuantity(quantity);
        cart.touch();
        return new CartMutation(cartData(cartItems.findByCartIdOrderByIdAsc(cart.getId())), cart.getGuestCartId());
    }

    @Transactional
    public CartMutation changeOptions(Long memberId, String guestCartId, Long cartItemId, CartOptionsCommand command) {
        Cart cart = required(memberId, guestCartId);
        CartItem item = ownedItem(cart, cartItemId);
        List<CheckoutCatalog.OptionSelection> options = command.selectedOptions() == null
            ? parseOptions(item.getSelectedOptions()) : command.selectedOptions();
        List<CheckoutCatalog.TextInput> textInputs = command.textInputs() == null
            ? parseTextInputs(item.getTextInputs()) : command.textInputs();
        int quantity = command.quantity() == null ? item.getQuantity() : command.quantity();
        catalog.quote(item.getProductId(), quantity, options, textInputs);
        item.changeOptions(optionKey(options), textKey(textInputs), quantity);
        cart.touch();
        return new CartMutation(cartData(cartItems.findByCartIdOrderByIdAsc(cart.getId())), cart.getGuestCartId());
    }

    @Transactional
    public void delete(Long memberId, String guestCartId, Long cartItemId) {
        Cart cart = required(memberId, guestCartId);
        cartItems.delete(ownedItem(cart, cartItemId));
        cart.touch();
    }

    @Transactional
    public void deleteAll(Long memberId, String guestCartId) {
        Cart cart = find(memberId, guestCartId);
        if (cart == null) {
            return;
        }
        cartItems.deleteByCartId(cart.getId());
        cart.touch();
    }

    @Transactional
    public CartMutation merge(Long memberId, String guestCartId) {
        if (guestCartId == null || guestCartId.isBlank()) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        Cart guest = carts.findByGuestCartId(guestCartId).orElse(null);
        Cart member = findOrCreate(memberId, null);
        if (guest == null || guest.getId().equals(member.getId())) {
            return new CartMutation(cartData(cartItems.findByCartIdOrderByIdAsc(member.getId())), null);
        }
        List<CartItem> memberItems = cartItems.findByCartIdOrderByIdAsc(member.getId());
        for (CartItem guestItem : cartItems.findByCartIdOrderByIdAsc(guest.getId())) {
            CartItem matching = memberItems.stream().filter(item -> item.getProductId().equals(guestItem.getProductId())
                && item.getSelectedOptions().equals(guestItem.getSelectedOptions()) && item.getTextInputs().equals(guestItem.getTextInputs()))
                .findFirst().orElse(null);
            int finalQuantity = matching == null ? guestItem.getQuantity()
                : Math.addExact(matching.getQuantity(), guestItem.getQuantity());
            catalog.quote(guestItem.getProductId(), finalQuantity, parseOptions(guestItem.getSelectedOptions()),
                parseTextInputs(guestItem.getTextInputs()));
            if (matching == null) {
                guestItem.moveTo(member.getId());
                memberItems.add(guestItem);
            } else {
                matching.addQuantity(guestItem.getQuantity());
                cartItems.delete(guestItem);
            }
        }
        carts.delete(guest);
        member.touch();
        return new CartMutation(cartData(memberItems), null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartLine> selectedItems(Long memberId, List<Long> cartItemIds) {
        if (cartItemIds == null || new HashSet<>(cartItemIds).size() != cartItemIds.size()) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
        if (cartItemIds.isEmpty()) {
            throw new BusinessRuleViolationException("장바구니가 비어 있습니다.");
        }
        Cart cart = carts.findByMemberId(memberId)
            .orElseThrow(() -> new BusinessRuleViolationException("장바구니가 비어 있습니다."));
        List<CartItem> all = cartItems.findByCartIdOrderByIdAsc(cart.getId());
        Set<Long> requested = Set.copyOf(cartItemIds);
        List<CartItem> selected = all.stream().filter(item -> requested.contains(item.getId()) && item.isSelected()).toList();
        if (selected.size() != requested.size()) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        return selected.stream().map(item -> new CartLine(item.getId(), item.getProductId(), item.getQuantity(),
            parseOptions(item.getSelectedOptions()), parseTextInputs(item.getTextInputs()))).toList();
    }

    @Override
    @Transactional
    public void removePurchased(Long memberId, List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            return;
        }
        Cart cart = carts.findByMemberId(memberId).orElse(null);
        if (cart == null) {
            return;
        }
        Set<Long> purchased = Set.copyOf(cartItemIds);
        cartItems.findByCartIdOrderByIdAsc(cart.getId()).stream()
            .filter(item -> purchased.contains(item.getId()))
            .forEach(cartItems::delete);
        cart.touch();
    }

    private Cart findOrCreate(Long memberId, String guestCartId) {
        Cart existing = find(memberId, guestCartId);
        if (existing != null) {
            return existing;
        }
        return memberId == null ? carts.save(Cart.guest(UUID.randomUUID().toString())) : carts.save(Cart.member(memberId));
    }

    private Cart required(Long memberId, String guestCartId) {
        Cart cart = find(memberId, guestCartId);
        if (cart == null) {
            throw new DomainException(ErrorCode.NOT_FOUND);
        }
        return cart;
    }

    private Cart find(Long memberId, String guestCartId) {
        return memberId != null ? carts.findByMemberId(memberId).orElse(null)
            : guestCartId == null || guestCartId.isBlank() ? null : carts.findByGuestCartId(guestCartId).orElse(null);
    }

    private CartItem ownedItem(Cart cart, Long cartItemId) {
        return cartItems.findById(cartItemId).filter(item -> item.getCartId().equals(cart.getId()))
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String optionKey(List<CheckoutCatalog.OptionSelection> options) {
        return json(options.stream().sorted(Comparator.comparing(CheckoutCatalog.OptionSelection::optionGroupId)
                .thenComparing(CheckoutCatalog.OptionSelection::choiceId))
            .map(option -> option.optionGroupId() + ":" + option.choiceId()).toList());
    }

    private String textKey(List<CheckoutCatalog.TextInput> inputs) {
        return json(inputs.stream().sorted(Comparator.comparing(CheckoutCatalog.TextInput::optionGroupId))
            .map(input -> input.optionGroupId() + ":" + input.text()).toList());
    }

    private List<CheckoutCatalog.OptionSelection> parseOptions(String value) {
        if (value == null || value.equals("[]")) return List.of();
        List<CheckoutCatalog.OptionSelection> result = new ArrayList<>();
        for (String token : jsonTokens(value)) {
            String[] pair = token.split(":", 2);
            result.add(new CheckoutCatalog.OptionSelection(Long.valueOf(pair[0]), Long.valueOf(pair[1])));
        }
        return result;
    }

    private List<CheckoutCatalog.TextInput> parseTextInputs(String value) {
        if (value == null || value.equals("[]")) return List.of();
        List<CheckoutCatalog.TextInput> result = new ArrayList<>();
        for (String token : jsonTokens(value)) {
            String[] pair = token.split(":", 2);
            result.add(new CheckoutCatalog.TextInput(Long.valueOf(pair[0]), pair.length == 2 ? pair[1] : ""));
        }
        return result;
    }

    private String json(List<String> values) {
        try {
            return json.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalStateException("장바구니 옵션을 저장할 수 없습니다.", exception);
        }
    }

    private List<String> jsonTokens(String value) {
        try {
            return List.of(json.readValue(value, String[].class));
        } catch (JacksonException exception) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private CartData cartData(List<CartItem> items) {
        Map<Long, List<EnrichedCartItem>> grouped = new LinkedHashMap<>();
        for (CartItem item : items) {
            CheckoutCatalog.CartProductView product = catalog.describe(item.getProductId(), item.getQuantity(),
                parseOptions(item.getSelectedOptions()), parseTextInputs(item.getTextInputs()));
            grouped.computeIfAbsent(product.artisanId(), ignored -> new ArrayList<>())
                .add(new EnrichedCartItem(item, product));
        }
        List<CartSection> sections = new ArrayList<>();
        long totalPrice = 0L;
        long totalShippingFee = 0L;
        int totalCount = 0;
        for (List<EnrichedCartItem> artisanItems : grouped.values()) {
            CheckoutCatalog.CartProductView first = artisanItems.getFirst().product();
            List<CartItemData> itemData = artisanItems.stream().map(EnrichedCartItem::data).toList();
            long selectedSubtotal = itemData.stream().filter(CartItemData::selected)
                .map(CartItemData::subtotal).reduce(0L, Math::addExact);
            long fee = artisanItems.stream().mapToLong(line -> line.product().shippingFee()).max().orElse(0L);
            Long threshold = artisanItems.stream().map(line -> line.product().freeShippingThreshold())
                .filter(java.util.Objects::nonNull).max(Long::compareTo).orElse(null);
            long chargedFee = selectedSubtotal == 0 || (threshold != null && selectedSubtotal >= threshold) ? 0L : fee;
            sections.add(new CartSection(first.artisanId(), first.artisanName(), first.certificationLevel(), itemData,
                fee, threshold));
            totalPrice = Math.addExact(totalPrice, selectedSubtotal);
            totalShippingFee = Math.addExact(totalShippingFee, chargedFee);
            totalCount = Math.addExact(totalCount, itemData.stream().mapToInt(CartItemData::quantity).sum());
        }
        return new CartData(List.copyOf(sections), totalPrice, totalShippingFee, totalCount);
    }

    public record CartCommand(Long productId, int quantity, List<CheckoutCatalog.OptionSelection> selectedOptions,
                              List<CheckoutCatalog.TextInput> textInputs) {}

    public record CartOptionsCommand(Integer quantity, List<CheckoutCatalog.OptionSelection> selectedOptions,
                                     List<CheckoutCatalog.TextInput> textInputs) {}

    public record CartItemData(Long cartItemId, Long productId, String productName, long unitPrice, int quantity,
                               long subtotal, List<CheckoutCatalog.ImageVariant> thumbnail, boolean isCustomOrder,
                               boolean soldOut, boolean selected,
                               List<CheckoutCatalog.SelectedOptionView> selectedOptions,
                               List<CheckoutCatalog.TextInputView> textInputs) {
    }

    public record CartSection(Long artisanId, String artisanName, String certificationLevel, List<CartItemData> items,
                              long shippingFee, Long freeShippingThreshold) {}

    public record CartData(List<CartSection> sections, long totalPrice, long totalShippingFee, int totalCount) {
        static CartData empty() { return new CartData(List.of(), 0L, 0L, 0); }
    }

    public record CartMutation(CartData cart, String guestCartId) {}

    private record EnrichedCartItem(CartItem item, CheckoutCatalog.CartProductView product) {
        CartItemData data() {
            long subtotal = Math.multiplyExact(product.unitPrice(), item.getQuantity());
            return new CartItemData(item.getId(), item.getProductId(), product.productName(), product.unitPrice(),
                item.getQuantity(), subtotal, product.thumbnail(), product.customOrder(), product.soldOut(),
                item.isSelected(), product.selectedOptions(), product.textInputs());
        }
    }
}
