#!/usr/bin/env bash
# Confluent Schema Registry에 Avro 스키마를 등록한다.
# subject 명명: {토픽명}-value (예: auction-events → auction-events-value)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -f "${SCRIPT_DIR}/../.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${SCRIPT_DIR}/../.env"
  set +a
fi

# 호스트에서 스크립트를 실행하는 기본 사용 경로를 우선한다.
# SCHEMA_REGISTRY_PUBLIC_URL > SCHEMA_REGISTRY_URL > localhost:${SCHEMA_REGISTRY_PORT}
REGISTRY_URL="${SCHEMA_REGISTRY_PUBLIC_URL:-${SCHEMA_REGISTRY_URL:-http://localhost:${SCHEMA_REGISTRY_PORT:-8085}}}"

register_subject() {
  local subject="$1"
  local avsc_file="$2"
  local body
  body="$(jq -n \
    --arg schema "$(jq -c . "${avsc_file}")" \
    '{schema: $schema}')"
  echo "POST ${REGISTRY_URL}/subjects/${subject}/versions"
  curl -sS -X POST "${REGISTRY_URL}/subjects/${subject}/versions" \
    -H "Content-Type: application/vnd.schemaregistry.v1+json" \
    -d "${body}"
  echo
}

register_subject "auction-events-value" "${SCRIPT_DIR}/AuctionEvent.avsc"
register_subject "bid-events-value" "${SCRIPT_DIR}/BidEvent.avsc"
register_subject "notification-events-value" "${SCRIPT_DIR}/NotificationEvent.avsc"
