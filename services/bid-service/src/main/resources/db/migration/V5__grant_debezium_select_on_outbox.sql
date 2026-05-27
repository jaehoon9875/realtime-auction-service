-- outbox_events SELECT 권한을 debezium CDC 계정에 명시적으로 부여한다.
-- debezium role은 GKE/Cloud SQL 환경에만 존재한다.
-- Testcontainers·CI DB에는 role이 없으므로, 존재할 때만 GRANT한다 (Flyway 기동 실패 방지).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'debezium') THEN
        GRANT SELECT ON TABLE outbox_events TO debezium;
    END IF;
END
$$;
