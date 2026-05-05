-- 경매 시작 시각 (예약 시작·스케줄러 PENDING→ONGOING 용)
ALTER TABLE auctions
    ADD COLUMN starts_at TIMESTAMP;

UPDATE auctions SET starts_at = created_at WHERE starts_at IS NULL;

ALTER TABLE auctions
    ALTER COLUMN starts_at SET NOT NULL;

CREATE INDEX idx_auctions_status_starts_at ON auctions (status, starts_at);
