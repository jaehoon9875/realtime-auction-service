#!/bin/bash
# Debezium이 PostgreSQL WAL을 읽기 위한 복제 연결 허용 설정
set -e

if [ -z "$DEBEZIUM_PASSWORD" ]; then
  echo "[bid-db] DEBEZIUM_PASSWORD is required."
  exit 1
fi

# SQL 문자열 리터럴 안전성을 위해 작은따옴표를 이스케이프한다.
escaped_debezium_password="${DEBEZIUM_PASSWORD//\'/\'\'}"

echo "host replication debezium all md5" >> "$PGDATA/pg_hba.conf"
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres -c "DO \$\$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'debezium') THEN CREATE ROLE debezium WITH REPLICATION LOGIN PASSWORD '${escaped_debezium_password}'; ELSE ALTER ROLE debezium WITH REPLICATION LOGIN PASSWORD '${escaped_debezium_password}'; END IF; END \$\$;"
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres -c "SELECT pg_reload_conf();"
echo "[bid-db] pg_hba.conf replication rule added and debezium role configured."
