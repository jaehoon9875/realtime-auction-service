-- Debezium 전용 복제 사용자 생성
-- REPLICATION: WAL 스트림 읽기 권한 / LOGIN: 원격 접속 허용
CREATE USER debezium WITH REPLICATION LOGIN PASSWORD 'debezium';

-- auction DB 접근 및 outbox_events 테이블 읽기 권한 부여
GRANT CONNECT ON DATABASE auction TO debezium;
GRANT USAGE ON SCHEMA public TO debezium;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO debezium;

-- 이후 생성되는 테이블에도 자동으로 SELECT 권한 부여
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO debezium;

-- pgoutput 플러그인이 변경 데이터를 읽기 위한 publication 생성
-- Debezium connector 설정의 plugin.name=pgoutput과 연동
CREATE PUBLICATION debezium_publication FOR ALL TABLES;
