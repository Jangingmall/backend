-- ============================================================
-- V1: 초기 스키마 (V1–V8 통합)
-- ============================================================

-- ── 회원 ────────────────────────────────────────────────────

CREATE TABLE member (
    member_id           BIGSERIAL       PRIMARY KEY,
    email               VARCHAR(255)    NOT NULL UNIQUE,
    password_hash       VARCHAR(255),
    name                VARCHAR(50)     NOT NULL,
    nickname            VARCHAR(50),
    profile_image_url   VARCHAR(500),
    withdrawal_reason   VARCHAR(500),
    phone               VARCHAR(20)     NOT NULL,
    role                VARCHAR(20)     NOT NULL,
    status              VARCHAR(30)     NOT NULL,
    age14_or_older      BOOLEAN         NOT NULL,
    terms_agreed        BOOLEAN         NOT NULL,
    privacy_agreed      BOOLEAN         NOT NULL,
    marketing_agreed    BOOLEAN         NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    updated_at          TIMESTAMP       NOT NULL,
    deleted_at          TIMESTAMP
);

CREATE TABLE member_social_account (
    id                  BIGSERIAL       PRIMARY KEY,
    member_id           BIGINT          NOT NULL,
    registration_id     VARCHAR(20)     NOT NULL,
    provider_user_id    VARCHAR(255)    NOT NULL,
    provider_email      VARCHAR(255),
    created_at          TIMESTAMP       NOT NULL,
    CONSTRAINT uq_social_account UNIQUE (registration_id, provider_user_id),
    CONSTRAINT fk_social_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

CREATE TABLE member_settings (
    member_id           BIGINT          PRIMARY KEY,
    dark_mode           BOOLEAN         NOT NULL DEFAULT FALSE,
    marketing           BOOLEAN         NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_settings_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

CREATE TABLE address (
    address_id          BIGSERIAL       PRIMARY KEY,
    member_id           BIGINT          NOT NULL,
    recipient_name      VARCHAR(50)     NOT NULL,
    phone               VARCHAR(20)     NOT NULL,
    zip_code            VARCHAR(10)     NOT NULL,
    address1            VARCHAR(255)    NOT NULL,
    address2            VARCHAR(255),
    is_default          BOOLEAN         NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_address_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

CREATE TABLE recent_view (
    recent_view_id      BIGSERIAL       PRIMARY KEY,
    member_id           BIGINT          NOT NULL,
    product_id          BIGINT          NOT NULL,
    viewed_at           TIMESTAMP       NOT NULL,
    CONSTRAINT uq_recent_view UNIQUE (member_id, product_id),
    CONSTRAINT fk_recent_view_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

CREATE TABLE seller_application (
    application_id              BIGSERIAL       PRIMARY KEY,
    member_id                   BIGINT          NOT NULL,
    business_name               VARCHAR(100)    NOT NULL,
    introduction                VARCHAR(255)    NOT NULL,
    business_license_image_url  VARCHAR(500)    NOT NULL,
    status                      VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    version                     INTEGER         NOT NULL DEFAULT 0,
    submitted_at                TIMESTAMP       NOT NULL,
    rejection_reason            TEXT,
    reviewed_by                 BIGINT,
    reviewed_at                 TIMESTAMP,
    document_review             VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    craftsmanship_review        VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    digital_conversion          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    order_system_integration    VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    qualification_tier          VARCHAR(40),
    CONSTRAINT fk_application_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

CREATE TABLE artisan_profile (
    artisan_id              BIGINT          PRIMARY KEY,
    business_name           VARCHAR(100)    NOT NULL,
    introduction            VARCHAR(255),
    profile_image_id        VARCHAR(30),
    profile_image_url       VARCHAR(500),
    certification_level     VARCHAR(20)     NOT NULL DEFAULT '일반',
    is_organization         BOOLEAN         NOT NULL DEFAULT FALSE,
    certified_year          SMALLINT,
    lineage                 VARCHAR(255),
    quote                   VARCHAR(500),
    bio                     TEXT,
    video_url               VARCHAR(500),
    category_code           VARCHAR(50),
    region                  VARCHAR(100),
    career_years            SMALLINT,
    certification_status    VARCHAR(20)     NOT NULL DEFAULT 'APPROVED',
    popularity_score        NUMERIC(10, 2)  NOT NULL DEFAULT 0,
    updated_at              TIMESTAMP       NOT NULL,
    CONSTRAINT fk_artisan_member FOREIGN KEY (artisan_id) REFERENCES member (member_id)
);

CREATE TABLE artisan_subscription (
    subscription_id         BIGSERIAL       PRIMARY KEY,
    member_id               BIGINT          NOT NULL,
    artisan_id              BIGINT          NOT NULL,
    notifications_enabled   BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP       NOT NULL,
    CONSTRAINT uq_artisan_subscription UNIQUE (member_id, artisan_id),
    CONSTRAINT fk_subscription_member  FOREIGN KEY (member_id)  REFERENCES member (member_id),
    CONSTRAINT fk_subscription_artisan FOREIGN KEY (artisan_id) REFERENCES member (member_id)
);

-- ── 상품 ────────────────────────────────────────────────────

CREATE TABLE category (
    category_id     BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(50)     NOT NULL
);

CREATE TABLE subcategory (
    subcategory_id  BIGSERIAL       PRIMARY KEY,
    category_id     BIGINT          NOT NULL,
    name            VARCHAR(50)     NOT NULL,
    CONSTRAINT fk_subcategory_category FOREIGN KEY (category_id) REFERENCES category (category_id)
);

CREATE TABLE subcategory_material (
    material_id     BIGSERIAL       PRIMARY KEY,
    subcategory_id  BIGINT          NOT NULL,
    name            VARCHAR(50)     NOT NULL,
    CONSTRAINT fk_material_subcategory FOREIGN KEY (subcategory_id) REFERENCES subcategory (subcategory_id)
);

CREATE TABLE product (
    product_id              BIGSERIAL       PRIMARY KEY,
    artisan_id              BIGINT          NOT NULL,
    category_id             BIGINT,
    subcategory_id          BIGINT,
    title                   VARCHAR(200)    NOT NULL,
    description             TEXT,
    material                VARCHAR(50),
    price                   INTEGER         NOT NULL,
    stock                   INTEGER         NOT NULL,
    thumbnail_url           VARCHAR(500),
    production_period_days  INTEGER,
    is_limited              BOOLEAN         NOT NULL DEFAULT FALSE,
    is_custom_order         BOOLEAN         NOT NULL DEFAULT FALSE,
    is_single_item          BOOLEAN         NOT NULL DEFAULT FALSE,
    has_gift_wrap           BOOLEAN         NOT NULL DEFAULT FALSE,
    status                  VARCHAR(20)     NOT NULL,
    created_at              TIMESTAMP       NOT NULL,
    updated_at              TIMESTAMP       NOT NULL,
    CONSTRAINT fk_product_artisan      FOREIGN KEY (artisan_id)     REFERENCES member (member_id),
    CONSTRAINT fk_product_category     FOREIGN KEY (category_id)    REFERENCES category (category_id),
    CONSTRAINT fk_product_subcategory  FOREIGN KEY (subcategory_id) REFERENCES subcategory (subcategory_id)
);

CREATE TABLE product_gift_theme (
    product_id  BIGINT       NOT NULL REFERENCES product(product_id) ON DELETE CASCADE,
    gift_theme  VARCHAR(50)  NOT NULL
);

CREATE TABLE product_purpose_tag (
    product_id  BIGINT       NOT NULL REFERENCES product(product_id) ON DELETE CASCADE,
    purpose_tag VARCHAR(100) NOT NULL
);

CREATE TABLE product_color (
    product_id  BIGINT      NOT NULL REFERENCES product(product_id) ON DELETE CASCADE,
    color       VARCHAR(20) NOT NULL
);

-- ── 이미지 업로드 (V5) ───────────────────────────────────────

CREATE TABLE image_upload (
    image_id       VARCHAR(30)              PRIMARY KEY,
    member_id      BIGINT                   NOT NULL,
    purpose        VARCHAR(20)              NOT NULL,
    source_width   INTEGER                  NOT NULL,
    source_height  INTEGER                  NOT NULL,
    variants       JSONB                    NOT NULL,
    consumed       BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_image_upload_member
        FOREIGN KEY (member_id) REFERENCES member (member_id)
);

-- ── 상품 이미지 / 콘텐츠 블록 (V7) ──────────────────────────

CREATE TABLE product_image (
    product_image_id BIGSERIAL PRIMARY KEY,
    product_id       BIGINT NOT NULL,
    image_id         VARCHAR(30) NOT NULL,
    display_order    SMALLINT NOT NULL DEFAULT 0,
    alt              VARCHAR(200),
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id)
        REFERENCES product (product_id) ON DELETE CASCADE,
    CONSTRAINT fk_product_image_upload FOREIGN KEY (image_id)
        REFERENCES image_upload (image_id),
    CONSTRAINT uk_product_image_order UNIQUE (product_id, display_order)
);

-- ── 챗봇 ────────────────────────────────────────────────────

CREATE TABLE chat_session (
    session_id  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id   BIGINT          NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at    TIMESTAMP,
    CONSTRAINT fk_chat_session_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

CREATE TABLE chat_message (
    message_id  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id  UUID            NOT NULL,
    sender      VARCHAR(10)     NOT NULL,
    content     TEXT            NOT NULL,
    sent_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session (session_id)
);

-- ── 콘텐츠 ──────────────────────────────────────────────────

CREATE TABLE interview (
    product_id  BIGINT          PRIMARY KEY,
    process     TEXT            NOT NULL,
    materials   VARCHAR(255)    NOT NULL,
    technique   VARCHAR(100)    NOT NULL,
    story       TEXT            NOT NULL,
    CONSTRAINT fk_interview_product FOREIGN KEY (product_id) REFERENCES product (product_id)
);

CREATE TABLE content_generation (
    id              BIGSERIAL       PRIMARY KEY,
    product_id      BIGINT          NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    images          TEXT            NOT NULL,
    product_name    VARCHAR(255)    NOT NULL,
    how_made        TEXT            NOT NULL,
    care_tips       TEXT            NOT NULL,
    requested_at    TIMESTAMP       NOT NULL,
    completed_at    TIMESTAMP,
    react_document  TEXT,
    job_id          VARCHAR(200),
    request_id      VARCHAR(200),
    idempotency_key VARCHAR(200),
    status_url      VARCHAR(500),
    CONSTRAINT fk_generation_product FOREIGN KEY (product_id) REFERENCES product (product_id)
);

CREATE TABLE content (
    content_id              BIGSERIAL       PRIMARY KEY,
    product_id              BIGINT          NOT NULL UNIQUE,
    status                  VARCHAR(20)     NOT NULL,
    version                 INTEGER         NOT NULL DEFAULT 0,
    fact_check_confirmed    BOOLEAN         NOT NULL DEFAULT FALSE,
    photo_match_confirmed   BOOLEAN         NOT NULL DEFAULT FALSE,
    display_approval_badge  BOOLEAN         NOT NULL DEFAULT FALSE,
    react_document          TEXT,
    created_at              TIMESTAMP       NOT NULL,
    updated_at              TIMESTAMP       NOT NULL,
    CONSTRAINT fk_content_product FOREIGN KEY (product_id) REFERENCES product (product_id)
);

CREATE TABLE content_edit_history (
    history_id          BIGSERIAL       PRIMARY KEY,
    content_id          BIGINT          NOT NULL,
    version             INTEGER         NOT NULL,
    edited_at           TIMESTAMP       NOT NULL,
    edited_by_type      VARCHAR(10)     NOT NULL,
    edited_by_member_id BIGINT,
    CONSTRAINT fk_history_content FOREIGN KEY (content_id) REFERENCES content (content_id)
);

CREATE TABLE content_block (
    block_id      BIGSERIAL PRIMARY KEY,
    content_id    BIGINT NOT NULL,
    display_order SMALLINT NOT NULL,
    tag           VARCHAR(10) NOT NULL,
    image_id      VARCHAR(30),
    video_url     VARCHAR(500),
    text          VARCHAR(2000),
    CONSTRAINT fk_content_block_content FOREIGN KEY (content_id)
        REFERENCES content (content_id) ON DELETE CASCADE,
    CONSTRAINT fk_content_block_upload FOREIGN KEY (image_id)
        REFERENCES image_upload (image_id),
    CONSTRAINT ck_content_block_tag CHECK (tag IN ('h2', 'p', 'img', 'video')),
    CONSTRAINT uk_content_block_order UNIQUE (content_id, display_order)
);

-- ── 알림 ────────────────────────────────────────────────────

CREATE TABLE notifications (
    id          BIGSERIAL       PRIMARY KEY,
    member_id   BIGINT          NOT NULL,
    title       VARCHAR(255)    NOT NULL,
    content     VARCHAR(255)    NOT NULL,
    status      VARCHAR(20)     NOT NULL,
    created_at  TIMESTAMP       NOT NULL,
    CONSTRAINT fk_notification_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

-- ── 위시리스트 (V3) ──────────────────────────────────────────

CREATE TABLE wishlist (
    wishlist_id    BIGSERIAL    PRIMARY KEY,
    member_id      BIGINT       NOT NULL,
    product_id     BIGINT       NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    CONSTRAINT uq_wishlist UNIQUE (member_id, product_id),
    CONSTRAINT fk_wishlist_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT fk_wishlist_product FOREIGN KEY (product_id) REFERENCES product (product_id)
);

-- ── 주문 / 리뷰 (V4 + V6) ───────────────────────────────────

CREATE TABLE orders (
    order_id            BIGSERIAL    PRIMARY KEY,
    order_number        VARCHAR(64)  NOT NULL UNIQUE,
    member_id           BIGINT       NOT NULL,
    address_id          BIGINT,
    recipient_name      VARCHAR(50)  NOT NULL,
    recipient_phone     VARCHAR(20)  NOT NULL,
    zip_code            VARCHAR(10)  NOT NULL,
    address1            VARCHAR(255) NOT NULL,
    address2            VARCHAR(255),
    status              VARCHAR(20)  NOT NULL,
    total_amount        BIGINT       NOT NULL,
    delivery_request    VARCHAR(100),
    payment_method      VARCHAR(20)  NOT NULL DEFAULT 'CARD',
    shipping_amount     BIGINT       NOT NULL DEFAULT 0,
    client_request_key  VARCHAR(100),
    canceled_at         TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL,
    CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

CREATE TABLE order_item (
    order_item_id                   BIGSERIAL    PRIMARY KEY,
    order_id                        BIGINT       NOT NULL,
    product_id                      BIGINT       NOT NULL,
    product_name_snapshot           VARCHAR(200) NOT NULL,
    price_snapshot                  BIGINT       NOT NULL,
    quantity                        INTEGER      NOT NULL,
    total_price                     BIGINT       NOT NULL DEFAULT 0,
    production_period_days_snapshot INTEGER,
    selected_options_snapshot       JSONB,
    CONSTRAINT fk_order_item_order   FOREIGN KEY (order_id)   REFERENCES orders (order_id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product (product_id)
);

CREATE TABLE product_review (
    review_id       BIGSERIAL PRIMARY KEY,
    product_id      BIGINT    NOT NULL,
    writer_id       BIGINT    NOT NULL,
    order_item_id   BIGINT    NOT NULL UNIQUE,
    rating          SMALLINT  NOT NULL,
    content         TEXT      NOT NULL,
    images          JSONB     NOT NULL DEFAULT '[]',
    created_at      TIMESTAMP NOT NULL,
    CONSTRAINT fk_review_product    FOREIGN KEY (product_id)    REFERENCES product (product_id),
    CONSTRAINT fk_review_writer     FOREIGN KEY (writer_id)     REFERENCES member (member_id),
    CONSTRAINT fk_review_order_item FOREIGN KEY (order_item_id) REFERENCES order_item (order_item_id)
);

-- ── 결제 (V6) ────────────────────────────────────────────────

CREATE TABLE cart (
    cart_id       BIGSERIAL PRIMARY KEY,
    member_id     BIGINT REFERENCES member(member_id),
    guest_cart_id VARCHAR(50),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cart_owner  CHECK ((member_id IS NULL) <> (guest_cart_id IS NULL)),
    CONSTRAINT uk_cart_member UNIQUE (member_id),
    CONSTRAINT uk_cart_guest  UNIQUE (guest_cart_id)
);

CREATE TABLE cart_item (
    cart_item_id     BIGSERIAL PRIMARY KEY,
    cart_id          BIGINT NOT NULL REFERENCES cart(cart_id) ON DELETE CASCADE,
    product_id       BIGINT NOT NULL REFERENCES product(product_id),
    quantity         INTEGER NOT NULL CHECK (quantity > 0),
    selected_options JSONB,
    text_inputs      JSONB,
    selected         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE payments (
    payment_id       BIGSERIAL PRIMARY KEY,
    order_id         BIGINT NOT NULL REFERENCES orders(order_id),
    amount           BIGINT NOT NULL CHECK (amount >= 0),
    method           VARCHAR(20) NOT NULL,
    payment_key      VARCHAR(255),
    status           VARCHAR(20) NOT NULL,
    failure_code     VARCHAR(100),
    failure_message  VARCHAR(255),
    version          BIGINT NOT NULL DEFAULT 0,
    approved_at      TIMESTAMP WITH TIME ZONE,
    canceled_at      TIMESTAMP WITH TIME ZONE,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payments_order       UNIQUE (order_id),
    CONSTRAINT uk_payments_payment_key UNIQUE (payment_key),
    CONSTRAINT ck_payments_method CHECK (method IN ('CARD', 'TRANSFER', 'VIRTUAL_ACCOUNT', 'MOBILE', 'EASY_PAY')),
    CONSTRAINT ck_payments_status CHECK (status IN ('READY', 'DONE', 'FAILED', 'CANCELED'))
);

CREATE TABLE payment_method (
    payment_method_id   BIGSERIAL PRIMARY KEY,
    member_id           BIGINT NOT NULL REFERENCES member(member_id),
    type                VARCHAR(20) NOT NULL,
    card_company        VARCHAR(50) NOT NULL,
    card_number_masked  VARCHAR(30) NOT NULL,
    card_fingerprint    VARCHAR(64) NOT NULL,
    is_default          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payment_method_member_fingerprint UNIQUE (member_id, card_fingerprint),
    CONSTRAINT ck_payment_method_type CHECK (type = 'CARD')
);

CREATE TABLE refund_account (
    refund_account_id       BIGSERIAL PRIMARY KEY,
    member_id               BIGINT NOT NULL REFERENCES member(member_id),
    bank_name               VARCHAR(50) NOT NULL,
    account_number_masked   VARCHAR(30) NOT NULL,
    account_fingerprint     VARCHAR(64) NOT NULL,
    account_holder          VARCHAR(50) NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_delivery (
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

CREATE TABLE order_return (
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
    CONSTRAINT ck_order_return_type   CHECK (type   IN ('RETURN', 'EXCHANGE')),
    CONSTRAINT ck_order_return_reason CHECK (reason IN ('CHANGE_OF_MIND', 'DEFECTIVE', 'WRONG_ITEM', 'WRONG_DELIVERY', 'OTHER')),
    CONSTRAINT ck_order_return_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'COMPLETED'))
);

-- ── 인덱스 ───────────────────────────────────────────────────

CREATE INDEX idx_product_artisan             ON product (artisan_id);
CREATE INDEX idx_product_status              ON product (status);
CREATE INDEX idx_content_generation_product  ON content_generation (product_id);
CREATE INDEX idx_history_content             ON content_edit_history (content_id);
CREATE INDEX idx_notification_member         ON notifications (member_id);
CREATE INDEX idx_recent_view_member          ON recent_view (member_id, viewed_at DESC);
CREATE INDEX idx_chat_session_member         ON chat_session (member_id);
CREATE INDEX idx_chat_message_session        ON chat_message (session_id, sent_at);
CREATE INDEX idx_wishlist_member             ON wishlist (member_id, wishlist_id DESC);
CREATE INDEX idx_orders_member               ON orders (member_id, order_id DESC);
CREATE INDEX idx_order_item_order            ON order_item (order_id, order_item_id);
CREATE INDEX idx_review_writer               ON product_review (writer_id, review_id DESC);
CREATE INDEX idx_image_upload_cleanup        ON image_upload (consumed, expires_at);
CREATE INDEX idx_image_upload_member         ON image_upload (member_id, image_id);
CREATE INDEX idx_product_image_product       ON product_image (product_id, display_order);
CREATE INDEX idx_content_block_content       ON content_block (content_id, display_order);
CREATE UNIQUE INDEX uk_orders_member_client_request
    ON orders(member_id, client_request_key)
    WHERE client_request_key IS NOT NULL;
CREATE INDEX ix_orders_expiration_scan       ON orders(status, updated_at);
CREATE INDEX ix_cart_item_cart               ON cart_item(cart_id);
CREATE INDEX ix_payment_method_member        ON payment_method(member_id);
CREATE INDEX ix_refund_account_member        ON refund_account(member_id);
