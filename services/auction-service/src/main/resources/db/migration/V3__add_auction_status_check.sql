-- 경매 상태를 PENDING | ACTIVE | CLOSED 세 값으로 제한한다.
-- 애플리케이션 레벨(AuctionStatus enum)과 DB 레벨을 동기화하여 잘못된 값의 저장을 원천 차단한다.
ALTER TABLE auctions
    ADD CONSTRAINT chk_auction_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'CLOSED'));
