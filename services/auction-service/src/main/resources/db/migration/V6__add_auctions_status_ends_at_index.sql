-- findIdsOngoingPastEnd 스케줄러 쿼리(WHERE status = ? AND ends_at <= ?) 풀 스캔 방지
CREATE INDEX idx_auctions_status_ends_at ON auctions (status, ends_at);
