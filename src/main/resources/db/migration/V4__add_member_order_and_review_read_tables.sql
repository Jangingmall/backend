CREATE TABLE IF NOT EXISTS orders (
    order_id          BIGSERIAL    PRIMARY KEY,
    order_number      VARCHAR(64)  NOT NULL UNIQUE,
    member_id         BIGINT       NOT NULL,
    address_id        BIGINT,
    recipient_name    VARCHAR(50)  NOT NULL,
    recipient_phone   VARCHAR(20)  NOT NULL,
    zip_code          VARCHAR(10)  NOT NULL,
    address1          VARCHAR(255) NOT NULL,
    address2          VARCHAR(255),
    status            VARCHAR(20)  NOT NULL,
    total_amount      BIGINT       NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

CREATE TABLE IF NOT EXISTS order_item (
    order_item_id          BIGSERIAL    PRIMARY KEY,
    order_id               BIGINT       NOT NULL,
    product_id             BIGINT       NOT NULL,
    product_name_snapshot  VARCHAR(200) NOT NULL,
    price_snapshot         BIGINT       NOT NULL,
    quantity               INTEGER      NOT NULL,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product (product_id)
);

CREATE TABLE IF NOT EXISTS product_review (
    review_id       BIGSERIAL PRIMARY KEY,
    product_id      BIGINT    NOT NULL,
    writer_id       BIGINT    NOT NULL,
    order_item_id   BIGINT    NOT NULL UNIQUE,
    rating          SMALLINT  NOT NULL,
    content         TEXT      NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES product (product_id),
    CONSTRAINT fk_review_writer FOREIGN KEY (writer_id) REFERENCES member (member_id),
    CONSTRAINT fk_review_order_item FOREIGN KEY (order_item_id) REFERENCES order_item (order_item_id)
);

CREATE INDEX IF NOT EXISTS idx_orders_member ON orders (member_id, order_id DESC);
CREATE INDEX IF NOT EXISTS idx_order_item_order ON order_item (order_id, order_item_id);
CREATE INDEX IF NOT EXISTS idx_review_writer ON product_review (writer_id, review_id DESC);
