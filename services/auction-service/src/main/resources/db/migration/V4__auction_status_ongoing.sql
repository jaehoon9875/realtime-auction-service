-- enum·문서와 동일하게 경매 진행 중 상태 값을 ONGOING 으로 통일한다.
-- (기존 마이그레이션 V3 는 ACTIVE 를 사용했으나 애플리케이션은 ONGOING 을 사용한다.)
ALTER TABLE auctions DROP CONSTRAINT IF EXISTS chk_auction_status;
UPDATE auctions SET status = 'ONGOING' WHERE status = 'ACTIVE';
ALTER TABLE auctions
    ADD CONSTRAINT chk_auction_status
        CHECK (status IN ('PENDING', 'ONGOING', 'CLOSED'));
