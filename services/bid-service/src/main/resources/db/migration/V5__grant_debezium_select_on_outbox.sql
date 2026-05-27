-- Cloud SQL에서 outbox_events 소유자(bid_user)가 debezium에 SELECT를 명시적으로 부여한다.
GRANT SELECT ON TABLE outbox_events TO debezium;
