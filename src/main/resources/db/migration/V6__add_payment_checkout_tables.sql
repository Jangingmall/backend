-- 결제/주문 도메인이 운영 DB에서도 사용할 수 있도록 결제 스키마를 추가한다.
-- V4에서 먼저 생성된 주문/주문상품 테이블은 기존 데이터를 보존하면서 보강한다.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS delivery_request VARCHAR(100),
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20) NOT NULL DEFAULT 'CARD',
    ADD COLUMN IF NOT EXISTS shipping_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS client_request_key VARCHAR(100),
    ADD COLUMN IF NOT EXISTS canceled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uk_orders_member_client_request
    ON orders(member_id, client_request_key)
    WHERE client_request_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_orders_expiration_scan
    ON orders(status, updated_at);

ALTER TABLE order_item
    ADD COLUMN IF NOT EXISTS total_price BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS production_period_days_snapshot INTEGER,
    ADD COLUMN IF NOT EXISTS selected_options_snapshot JSONB;

UPDATE order_item
SET total_price = price_snapshot * quantity
WHERE total_price = 0;

CREATE TABLE IF NOT EXISTS cart (
    cart_id       BIGSERIAL PRIMARY KEY,
    member_id     BIGINT REFERENCES member(member_id),
    guest_cart_id VARCHAR(50),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cart_owner CHECK ((member_id IS NULL) <> (guest_cart_id IS NULL)),
    CONSTRAINT uk_cart_member UNIQUE (member_id),
    CONSTRAINT uk_cart_guest UNIQUE (guest_cart_id)
);

CREATE TABLE IF NOT EXISTS cart_item (
    cart_item_id     BIGSERIAL PRIMARY KEY,
    cart_id          BIGINT NOT NULL REFERENCES cart(cart_id) ON DELETE CASCADE,
    product_id       BIGINT NOT NULL REFERENCES product(product_id),
    quantity         INTEGER NOT NULL CHECK (quantity > 0),
    selected_options JSONB,
    text_inputs      JSONB,
    selected         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS ix_cart_item_cart ON cart_item(cart_id);

CREATE TABLE IF NOT EXISTS payments (
    payment_id     BIGSERIAL PRIMARY KEY,
    order_id       BIGINT NOT NULL REFERENCES orders(order_id),
    amount         BIGINT NOT NULL CHECK (amount >= 0),
    method         VARCHAR(20) NOT NULL,
    payment_key    VARCHAR(255),
    status         VARCHAR(20) NOT NULL,
    failure_code   VARCHAR(100),
    failure_message VARCHAR(255),
    version        BIGINT NOT NULL DEFAULT 0,
    approved_at    TIMESTAMP WITH TIME ZONE,
    canceled_at    TIMESTAMP WITH TIME ZONE,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payments_order UNIQUE (order_id),
    CONSTRAINT uk_payments_payment_key UNIQUE (payment_key),
    CONSTRAINT ck_payments_method CHECK (method IN ('CARD', 'TRANSFER', 'VIRTUAL_ACCOUNT', 'MOBILE', 'EASY_PAY')),
    CONSTRAINT ck_payments_status CHECK (status IN ('READY', 'DONE', 'FAILED', 'CANCELED'))
);

CREATE TABLE IF NOT EXISTS payment_method (
    payment_method_id BIGSERIAL PRIMARY KEY,
    member_id         BIGINT NOT NULL REFERENCES member(member_id),
    type              VARCHAR(20) NOT NULL,
    card_company      VARCHAR(50) NOT NULL,
    card_number_masked VARCHAR(30) NOT NULL,
    card_fingerprint  VARCHAR(64) NOT NULL,
    is_default        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_method_member_fingerprint UNIQUE (member_id, card_fingerprint),
    CONSTRAINT ck_payment_method_type CHECK (type = 'CARD')
);

CREATE INDEX IF NOT EXISTS ix_payment_method_member ON payment_method(member_id);

CREATE TABLE IF NOT EXISTS refund_account (
    refund_account_id BIGSERIAL PRIMARY KEY,
    member_id         BIGINT NOT NULL REFERENCES member(member_id),
    bank_name         VARCHAR(50) NOT NULL,
    account_number_masked VARCHAR(30) NOT NULL,
    account_fingerprint  VARCHAR(64) NOT NULL,
    account_holder    VARCHAR(50) NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_refund_account_member ON refund_account(member_id);

CREATE TABLE IF NOT EXISTS order_delivery (
    order_delivery_id BIGSERIAL PRIMARY KEY,
    order_id          BIGINT NOT NULL REFERENCES orders(order_id),
    carrier_code      VARCHAR(20) NOT NULL,
    carrier_name      VARCHAR(100) NOT NULL,
    tracking_number   VARCHAR(100) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_delivery_order_id UNIQUE (order_id),
    CONSTRAINT ck_order_delivery_status CHECK (status IN ('SHIPPED', 'IN_TRANSIT', 'DELIVERED'))
);

CREATE TABLE IF NOT EXISTS order_return (
    return_id         BIGSERIAL PRIMARY KEY,
    order_id          BIGINT NOT NULL REFERENCES orders(order_id),
    type              VARCHAR(20) NOT NULL,
    reason            VARCHAR(30) NOT NULL,
    reason_detail     VARCHAR(500),
    return_address_id BIGINT NOT NULL REFERENCES address(address_id),
    order_item_ids    JSONB NOT NULL DEFAULT '[]'::JSONB,
    image_ids         JSONB,
    status            VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    version           BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_return_order_id UNIQUE (order_id),
    CONSTRAINT ck_order_return_type CHECK (type IN ('RETURN', 'EXCHANGE')),
    CONSTRAINT ck_order_return_reason CHECK (reason IN ('CHANGE_OF_MIND', 'DEFECTIVE', 'WRONG_ITEM', 'WRONG_DELIVERY', 'OTHER')),
    CONSTRAINT ck_order_return_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'COMPLETED'))
);
