#!/usr/bin/env bash
# Kafka Connect(Debezium 컨테이너)에 auction-outbox 커넥터를 등록한다.
# database.password 는 저장소에 넣지 않고 DEBEZIUM_PASSWORD 환경변수로 주입한다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONNECTOR_JSON="${SCRIPT_DIR}/connectors/auction-outbox-connector.json"

if [[ -f "${SCRIPT_DIR}/../.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${SCRIPT_DIR}/../.env"
  set +a
fi

CONNECT_URL="${KAFKA_CONNECT_URL:-http://localhost:${DEBEZIUM_PORT:-8083}}"

if [[ -z "${DEBEZIUM_PASSWORD:-}" ]]; then
  echo "DEBEZIUM_PASSWORD 가 필요합니다. infra/.env 에 설정하거나 환경변수로 지정하세요." >&2
  exit 1
fi

BODY="$(jq --arg pwd "${DEBEZIUM_PASSWORD}" '.config["database.password"] = $pwd' "${CONNECTOR_JSON}")"

echo "POST ${CONNECT_URL}/connectors"
curl -fsS -X POST "${CONNECT_URL}/connectors" \
  -H "Content-Type: application/json" \
  -d "${BODY}"

echo
