CREATE TABLE bids (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auction_id  UUID NOT NULL,
    bidder_id   UUID NOT NULL,
    amount      BIGINT NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED',
    placed_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_bids_auction_id ON bids(auction_id);
CREATE INDEX idx_bids_bidder_id ON bids(bidder_id);
