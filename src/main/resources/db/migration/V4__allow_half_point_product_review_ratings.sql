-- Preserve existing integer ratings while allowing the frontend's 0.5-point scale.
ALTER TABLE product_review
    ALTER COLUMN rating SET DATA TYPE NUMERIC(2, 1);
