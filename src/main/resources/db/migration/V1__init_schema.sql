-- ============================================================
-- V1: 초기 스키마 (전체 엔티티 기준)
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
    react_document TEXT,
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

-- ── 인덱스 ───────────────────────────────────────────────────

CREATE INDEX idx_product_artisan             ON product (artisan_id);
CREATE INDEX idx_product_status              ON product (status);
CREATE INDEX idx_content_generation_product  ON content_generation (product_id);
CREATE INDEX idx_history_content             ON content_edit_history (content_id);
CREATE INDEX idx_notification_member         ON notifications (member_id);
CREATE INDEX idx_recent_view_member          ON recent_view (member_id, viewed_at DESC);
CREATE INDEX idx_chat_session_member         ON chat_session (member_id);
CREATE INDEX idx_chat_message_session        ON chat_message (session_id, sent_at);
