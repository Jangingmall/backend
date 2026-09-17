-- Connect uploaded image aggregates to product and content bounded contexts.
CREATE TABLE IF NOT EXISTS product_image (
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

CREATE INDEX IF NOT EXISTS ix_product_image_product
    ON product_image (product_id, display_order);

CREATE TABLE IF NOT EXISTS content_block (
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

CREATE INDEX IF NOT EXISTS ix_content_block_content
    ON content_block (content_id, display_order);
