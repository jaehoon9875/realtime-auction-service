-- Cloud SQL에서 outbox_events 소유자(auction_user)가 debezium에 SELECT를 명시적으로 부여한다.
-- init 스크립트의 GRANT SELECT ON ALL TABLES는 마이그레이션 이후 생성 테이블에 자동 적용되지 않을 수 있다.
GRANT SELECT ON TABLE outbox_events TO debezium;
