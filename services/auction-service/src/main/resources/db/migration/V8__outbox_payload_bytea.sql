-- Outbox payload를 JSONB에서 Confluent Avro wire format(BYTEA)로 전환한다.
-- 기존 JSONB 레코드는 Avro 바이너리로 변환할 수 없으므로 삭제한다.
TRUNCATE TABLE outbox_events;

ALTER TABLE outbox_events DROP COLUMN payload;
ALTER TABLE outbox_events ADD COLUMN payload BYTEA NOT NULL;
