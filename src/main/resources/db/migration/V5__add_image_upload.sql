CREATE TABLE IF NOT EXISTS image_upload (
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

CREATE INDEX IF NOT EXISTS idx_image_upload_cleanup
    ON image_upload (consumed, expires_at);

CREATE INDEX IF NOT EXISTS idx_image_upload_member
    ON image_upload (member_id, image_id);
