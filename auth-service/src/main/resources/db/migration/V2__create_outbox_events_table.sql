CREATE TABLE outbox_events (
    id           UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    topic        VARCHAR(255) NOT NULL,
    payload      JSONB     NOT NULL,
    processed    BOOLEAN   NOT NULL DEFAULT false,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    processed_at TIMESTAMP
);
