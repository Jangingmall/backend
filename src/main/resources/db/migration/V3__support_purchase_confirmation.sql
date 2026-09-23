-- Customer purchase confirmation is a persisted terminal order state. Existing delivered orders remain unchanged.
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS purchase_confirmed_at TIMESTAMP;
