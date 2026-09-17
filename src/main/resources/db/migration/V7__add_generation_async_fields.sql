ALTER TABLE content_generation
    ADD COLUMN IF NOT EXISTS job_id           VARCHAR(200),
    ADD COLUMN IF NOT EXISTS request_id       VARCHAR(200),
    ADD COLUMN IF NOT EXISTS idempotency_key  VARCHAR(200),
    ADD COLUMN IF NOT EXISTS status_url       VARCHAR(500);
