#!/bin/bash
# Debezium이 PostgreSQL WAL을 읽기 위한 복제 연결 허용 설정
# pg_hba.conf에 replication 항목 추가 후 reload
set -e

echo "host replication debezium all md5" >> "$PGDATA/pg_hba.conf"
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres -c "SELECT pg_reload_conf();"
echo "[auction-db] pg_hba.conf replication rule added."
