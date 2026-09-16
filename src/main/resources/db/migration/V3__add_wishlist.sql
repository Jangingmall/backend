CREATE TABLE IF NOT EXISTS wishlist (
    wishlist_id    BIGSERIAL    PRIMARY KEY,
    member_id      BIGINT       NOT NULL,
    product_id     BIGINT       NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    CONSTRAINT uq_wishlist UNIQUE (member_id, product_id),
    CONSTRAINT fk_wishlist_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT fk_wishlist_product FOREIGN KEY (product_id) REFERENCES product (product_id)
);

CREATE INDEX IF NOT EXISTS idx_wishlist_member ON wishlist (member_id, wishlist_id DESC);
