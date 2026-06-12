ALTER TABLE outbox_events
    ADD COLUMN event_key UUID;

-- 기존 Outbox 행은 snapshot.mode=never 정책상 재발행 대상이 아니다.
-- NOT NULL 전환을 위해 기존 행은 현재 aggregate_id로 채우고, 신규 행부터 auctionId를 저장한다.
UPDATE outbox_events
SET event_key = aggregate_id
WHERE event_key IS NULL;

ALTER TABLE outbox_events
    ALTER COLUMN event_key SET NOT NULL;
