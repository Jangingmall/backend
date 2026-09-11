package com.jangingmall.backend.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.payment.domain.Cart;
import com.jangingmall.backend.payment.domain.CartItem;
import com.jangingmall.backend.payment.domain.CartItemRepository;
import com.jangingmall.backend.payment.domain.CartRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock private CartRepository carts;
    @Mock private CartItemRepository cartItems;
    @Mock private CheckoutCatalog catalog;
    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(carts, cartItems, catalog, new ObjectMapper());
        lenient().when(catalog.describe(anyLong(), anyInt(), anyList(), anyList()))
            .thenAnswer(invocation -> view(invocation.getArgument(0), invocation.getArgument(1)));
    }

    @Test
    @DisplayName("PAY-P0-001/002 회원과 guestCartId 쿠키 장바구니를 조회한다")
    void getsMemberAndGuestCarts() {
        Cart member = cart(1L, null, 10L);
        Cart guest = cart(null, "guest-1", 20L);
        when(carts.findByMemberId(1L)).thenReturn(Optional.of(member));
        when(carts.findByGuestCartId("guest-1")).thenReturn(Optional.of(guest));
        when(cartItems.findByCartIdOrderByIdAsc(10L)).thenReturn(List.of(item(10L, 100L, 7L, 2)));
        when(cartItems.findByCartIdOrderByIdAsc(20L)).thenReturn(List.of(item(20L, 200L, 8L, 1)));

        assertThat(service.get(1L, null).totalCount()).isEqualTo(2);
        assertThat(service.get(null, "guest-1").totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("PAY-P0-003/004 빈 장바구니와 식별자 없는 게스트는 빈 sections를 반환한다")
    void returnsEmptyCart() {
        assertThat(service.get(null, null).sections()).isEmpty();
        when(carts.findByMemberId(1L)).thenReturn(Optional.of(cart(1L, null, 10L)));
        when(cartItems.findByCartIdOrderByIdAsc(10L)).thenReturn(List.of());
        assertThat(service.get(1L, null).sections()).isEmpty();
    }

    @Test
    @DisplayName("PAY-P0-006 게스트 상품 추가는 새 장바구니와 쿠키 식별자를 만든다")
    void addsGuestCartItem() {
        Cart savedCart = cart(null, "generated", 10L);
        CartItem savedItem = item(10L, 100L, 7L, 2);
        when(carts.save(org.mockito.ArgumentMatchers.any())).thenReturn(savedCart);
        when(cartItems.save(org.mockito.ArgumentMatchers.any())).thenReturn(savedItem);
        when(cartItems.findByCartIdOrderByIdAsc(10L)).thenReturn(List.of(savedItem));

        CartService.CartMutation result = service.add(null, null,
            new CartService.CartCommand(7L, 2, List.of(), List.of()));

        assertThat(result.guestCartId()).isEqualTo("generated");
        assertThat(result.cart().totalCount()).isEqualTo(2);
        verify(catalog).quote(7L, 2, List.of(), List.of());
    }

    @Test
    @DisplayName("PAY-P0-007 동일 상품·옵션 재추가는 최종 수량으로 재고를 검증한 뒤 합산한다")
    void mergesSameCartItemAfterFinalStockCheck() {
        Cart cart = cart(1L, null, 10L);
        CartItem item = item(10L, 100L, 7L, 2);
        when(carts.findByMemberId(1L)).thenReturn(Optional.of(cart));
        when(cartItems.findByCartIdOrderByIdAsc(10L)).thenReturn(List.of(item));

        service.add(1L, null, new CartService.CartCommand(7L, 3, List.of(), List.of()));

        assertThat(item.getQuantity()).isEqualTo(5);
        verify(catalog).quote(7L, 5, List.of(), List.of());
        verify(cartItems, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("PAY-P0-008/009/010/012/013 상품·필수옵션·재고 검증 오류를 그대로 거부한다")
    void rejectsInvalidCatalogSelection() {
        doThrow(new BusinessRuleViolationException("필수 옵션 누락")).when(catalog)
            .quote(7L, 1, List.of(), List.of());
        assertThatThrownBy(() -> service.add(1L, null,
            new CartService.CartCommand(7L, 1, List.of(), List.of())))
            .isInstanceOf(BusinessRuleViolationException.class);

        doThrow(new DomainException(ErrorCode.NOT_FOUND)).when(catalog)
            .quote(8L, 1, List.of(), List.of());
        assertThatThrownBy(() -> service.add(1L, null,
            new CartService.CartCommand(8L, 1, List.of(), List.of())))
            .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("PAY-P0-014/016 정상 수량 변경은 저장하고 재고 초과는 변경하지 않는다")
    void changesQuantityOnlyAfterValidation() {
        Cart cart = cart(1L, null, 10L);
        CartItem item = item(10L, 100L, 7L, 2);
        when(carts.findByMemberId(1L)).thenReturn(Optional.of(cart));
        when(cartItems.findById(100L)).thenReturn(Optional.of(item));
        when(cartItems.findByCartIdOrderByIdAsc(10L)).thenReturn(List.of(item));

        service.changeQuantity(1L, null, 100L, 3);
        assertThat(item.getQuantity()).isEqualTo(3);

        doThrow(new BusinessRuleViolationException("재고 부족")).when(catalog)
            .quote(7L, 99, List.of(), List.of());
        assertThatThrownBy(() -> service.changeQuantity(1L, null, 100L, 99))
            .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(item.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("PAY-P0-017/018 존재하지 않거나 다른 사용자의 cartItem은 404로 감춘다")
    void hidesUnknownOrForeignCartItem() {
        Cart cart = cart(1L, null, 10L);
        when(carts.findByMemberId(1L)).thenReturn(Optional.of(cart));
        when(cartItems.findById(100L)).thenReturn(Optional.empty());
        assertNotFound(() -> service.changeQuantity(1L, null, 100L, 1));

        when(cartItems.findById(101L)).thenReturn(Optional.of(item(99L, 101L, 7L, 1)));
        assertNotFound(() -> service.changeQuantity(1L, null, 101L, 1));
    }

    @Test
    @DisplayName("PAY-P0-019/020/021/022 옵션 변경은 새 조합·수량 검증을 통과해야 반영한다")
    void changesOptionsOnlyAfterValidation() {
        Cart cart = cart(1L, null, 10L);
        CartItem item = item(10L, 100L, 7L, 1);
        when(carts.findByMemberId(1L)).thenReturn(Optional.of(cart));
        when(cartItems.findById(100L)).thenReturn(Optional.of(item));
        when(cartItems.findByCartIdOrderByIdAsc(10L)).thenReturn(List.of(item));
        List<CheckoutCatalog.OptionSelection> options = List.of(new CheckoutCatalog.OptionSelection(5L, 6L));

        service.changeOptions(1L, null, 100L, new CartService.CartOptionsCommand(2, options, List.of()));
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getSelectedOptions()).contains("5:6");

        doThrow(new BusinessRuleViolationException("옵션 재고 부족")).when(catalog)
            .quote(7L, 3, options, List.of());
        assertThatThrownBy(() -> service.changeOptions(1L, null, 100L,
            new CartService.CartOptionsCommand(3, options, List.of())))
            .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(item.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("PAY-P0-023/024/025 본인 항목만 삭제할 수 있다")
    void deletesOnlyOwnedItem() {
        Cart cart = cart(1L, null, 10L);
        CartItem owned = item(10L, 100L, 7L, 1);
        when(carts.findByMemberId(1L)).thenReturn(Optional.of(cart));
        when(cartItems.findById(100L)).thenReturn(Optional.of(owned));
        service.delete(1L, null, 100L);
        verify(cartItems).delete(owned);

        when(cartItems.findById(101L)).thenReturn(Optional.empty());
        assertNotFound(() -> service.delete(1L, null, 101L));
        when(cartItems.findById(102L)).thenReturn(Optional.of(item(99L, 102L, 7L, 1)));
        assertNotFound(() -> service.delete(1L, null, 102L));
    }

    @Test
    @DisplayName("PAY-P1-001/002 장바구니 전체 삭제는 빈 장바구니에도 멱등이다")
    void deletesAllIdempotently() {
        when(carts.findByMemberId(1L)).thenReturn(Optional.of(cart(1L, null, 10L)));
        service.deleteAll(1L, null);
        verify(cartItems).deleteByCartId(10L);

        service.deleteAll(null, null);
    }

    @Test
    @DisplayName("PAY-P0-026/027 게스트 장바구니는 동일 조합을 합산해 회원 장바구니로 병합한다")
    void mergesGuestCart() {
        Cart guest = cart(null, "guest-1", 20L);
        Cart member = cart(1L, null, 10L);
        CartItem memberItem = item(10L, 100L, 7L, 2);
        CartItem guestItem = item(20L, 200L, 7L, 3);
        when(carts.findByGuestCartId("guest-1")).thenReturn(Optional.of(guest));
        when(carts.findByMemberId(1L)).thenReturn(Optional.of(member));
        when(cartItems.findByCartIdOrderByIdAsc(10L)).thenReturn(List.of(memberItem));
        when(cartItems.findByCartIdOrderByIdAsc(20L)).thenReturn(List.of(guestItem));

        CartService.CartMutation result = service.merge(1L, "guest-1");

        assertThat(memberItem.getQuantity()).isEqualTo(5);
        assertThat(result.cart().totalCount()).isEqualTo(5);
        verify(catalog).quote(7L, 5, List.of(), List.of());
        verify(cartItems).delete(guestItem);
        verify(carts).delete(guest);
    }

    @Test
    @DisplayName("PAY-P0-028 병합 후 최종 수량이 재고를 넘으면 전체 병합을 실패시킨다")
    void rejectsMergeOverStock() {
        Cart guest = cart(null, "guest-1", 20L);
        Cart member = cart(1L, null, 10L);
        CartItem memberItem = item(10L, 100L, 7L, 2);
        CartItem guestItem = item(20L, 200L, 7L, 3);
        when(carts.findByGuestCartId("guest-1")).thenReturn(Optional.of(guest));
        when(carts.findByMemberId(1L)).thenReturn(Optional.of(member));
        when(cartItems.findByCartIdOrderByIdAsc(10L)).thenReturn(List.of(memberItem));
        when(cartItems.findByCartIdOrderByIdAsc(20L)).thenReturn(List.of(guestItem));
        doThrow(new BusinessRuleViolationException("재고 부족")).when(catalog)
            .quote(7L, 5, List.of(), List.of());

        assertThatThrownBy(() -> service.merge(1L, "guest-1"))
            .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(memberItem.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("PAY-P0-029 guestCartId 없이 병합하면 400 INVALID_INPUT이다")
    void rejectsMergeWithoutGuestId() {
        assertThatThrownBy(() -> service.merge(1L, null)).isInstanceOf(DomainException.class)
            .extracting(error -> ((DomainException) error).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("PAY-P0-032 주문 대상이 없는 장바구니는 422다")
    void rejectsEmptyCheckoutCart() {
        assertThatThrownBy(() -> service.selectedItems(1L, List.of()))
            .isInstanceOf(BusinessRuleViolationException.class);
        when(carts.findByMemberId(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.selectedItems(1L, List.of(10L)))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("장바구니 응답은 장인 sections·현재 가격·배송비 합계를 계산한다")
    void buildsSectionedCartResponse() {
        Cart cart = cart(1L, null, 10L);
        CartItem item = item(10L, 100L, 7L, 2);
        when(carts.findByMemberId(1L)).thenReturn(Optional.of(cart));
        when(cartItems.findByCartIdOrderByIdAsc(10L)).thenReturn(List.of(item));

        CartService.CartData result = service.get(1L, null);

        assertThat(result.sections()).singleElement().satisfies(section -> {
            assertThat(section.artisanId()).isEqualTo(3L);
            assertThat(section.items()).singleElement().satisfies(data -> assertThat(data.subtotal()).isEqualTo(20_000L));
        });
        assertThat(result.totalPrice()).isEqualTo(20_000L);
        assertThat(result.totalShippingFee()).isEqualTo(3_000L);
    }

    private CheckoutCatalog.CartProductView view(Long productId, int quantity) {
        return new CheckoutCatalog.CartProductView(productId, "상품 " + productId, 10_000L, 3L, "김장인", "이수자",
            List.of(), false, false, 3_000L, 50_000L, List.of(), List.of());
    }

    private Cart cart(Long memberId, String guestCartId, Long id) {
        Cart cart = memberId == null ? Cart.guest(guestCartId) : Cart.member(memberId);
        ReflectionTestUtils.setField(cart, "id", id);
        return cart;
    }

    private CartItem item(Long cartId, Long id, Long productId, int quantity) {
        CartItem item = new CartItem(cartId, productId, quantity, "[]", "[]");
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOf(DomainException.class)
            .extracting(error -> ((DomainException) error).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
    }
}
