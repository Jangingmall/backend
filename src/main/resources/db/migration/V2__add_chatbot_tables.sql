CREATE TABLE chat_session (
    session_id  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id   BIGINT          NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at    TIMESTAMP,
    CONSTRAINT fk_chat_session_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

CREATE INDEX idx_chat_session_member ON chat_session (member_id);

CREATE TABLE chat_message (
    message_id  BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id  UUID            NOT NULL,
    sender      VARCHAR(10)     NOT NULL,
    content     TEXT            NOT NULL,
    sent_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session (session_id)
);

CREATE INDEX idx_chat_message_session ON chat_message (session_id, sent_at);
