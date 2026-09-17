package com.jangingmall.backend.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "cart_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_id")
    private Long id;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_options", columnDefinition = "jsonb")
    private String selectedOptions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "text_inputs", columnDefinition = "jsonb")
    private String textInputs;

    @Column(nullable = false)
    private boolean selected;

    public CartItem(Long cartId, Long productId, int quantity, String selectedOptions, String textInputs) {
        this.cartId = cartId;
        this.productId = productId;
        this.quantity = quantity;
        this.selectedOptions = selectedOptions;
        this.textInputs = textInputs;
        this.selected = true;
    }

    public void changeQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void changeOptions(String selectedOptions, String textInputs, int quantity) {
        this.selectedOptions = selectedOptions;
        this.textInputs = textInputs;
        this.quantity = quantity;
    }

    public void addQuantity(int quantity) {
        this.quantity = Math.addExact(this.quantity, quantity);
    }

    public void moveTo(Long cartId) {
        this.cartId = cartId;
    }
}
