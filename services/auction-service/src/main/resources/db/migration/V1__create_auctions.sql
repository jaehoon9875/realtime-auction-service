CREATE TABLE auctions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id   UUID NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    start_price BIGINT NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING: 시작 전 | ONGOING: 진행 중 | CLOSED: 마감 | CANCELLED: 취소
    ends_at     TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);