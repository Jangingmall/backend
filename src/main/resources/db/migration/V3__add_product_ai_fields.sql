ALTER TABLE product ADD COLUMN production_period_days INTEGER;

CREATE TABLE product_gift_theme (
    product_id BIGINT NOT NULL REFERENCES product(product_id) ON DELETE CASCADE,
    gift_theme VARCHAR(50) NOT NULL
);

CREATE TABLE product_purpose_tag (
    product_id BIGINT NOT NULL REFERENCES product(product_id) ON DELETE CASCADE,
    purpose_tag VARCHAR(100) NOT NULL
);

CREATE TABLE product_color (
    product_id BIGINT NOT NULL REFERENCES product(product_id) ON DELETE CASCADE,
    color VARCHAR(20) NOT NULL
);
