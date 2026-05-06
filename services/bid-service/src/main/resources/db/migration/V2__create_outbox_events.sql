CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50) NOT NULL,   -- 'BID'
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,   -- 'BID_PLACED' | 'BID_REJECTED'
    payload         JSONB NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
