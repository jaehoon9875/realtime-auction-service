#!/usr/bin/env bash
# Kafka Connect(Debezium 컨테이너)에 outbox 커넥터를 등록/삭제한다.
# database.password 와 schema registry 주소는 환경변수로 주입한다.
# 기본 동작은 "등록"이며, --recreate / --delete-only 옵션으로 동작을 제어한다.
set -euo pipefail

# 스크립트 위치를 기준으로 커넥터 JSON 경로를 계산한다.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONNECTOR_JSON="${SCRIPT_DIR}/connectors/auction-outbox-connector.json"
CONNECTOR_JSON_BID="${SCRIPT_DIR}/connectors/bid-outbox-connector.json"
CONNECTOR_NAME_AUCTION="auction-outbox-connector"
CONNECTOR_NAME_BID="bid-outbox-connector"

# infra/.env가 있으면 자동 로드해서 환경변수를 세팅한다.
if [[ -f "${SCRIPT_DIR}/../.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${SCRIPT_DIR}/../.env"
  set +a
fi

# Kafka Connect 주소를 환경변수 또는 기본값(localhost:DEBEZIUM_PORT)으로 결정한다.
CONNECT_URL="${KAFKA_CONNECT_URL:-http://localhost:${DEBEZIUM_PORT:-8083}}"
# 커넥터 태스크는 Connect 컨테이너 내부에서 동작하므로 기본값은 컨테이너 DNS를 사용한다.
# 필요 시 SCHEMA_REGISTRY_URL 환경변수로 localhost:${SCHEMA_REGISTRY_PORT} 같은 값으로 오버라이드할 수 있다.
SCHEMA_REGISTRY_URL_VALUE="${SCHEMA_REGISTRY_URL:-http://schema-registry:8081}"

ACTION="create"

usage() {
  cat <<'EOF'
Usage: ./register-connectors.sh [option]

Options:
  --recreate     기존 커넥터를 먼저 삭제한 뒤 재등록
  --delete-only  커넥터만 삭제하고 종료
  -h, --help     도움말 출력

Environment variables:
  DEBEZIUM_PASSWORD  (required for create/recreate)
  KAFKA_CONNECT_URL  (default: http://localhost:${DEBEZIUM_PORT:-8083})
  SCHEMA_REGISTRY_URL (default: http://schema-registry:8081)
EOF
}

if [[ $# -gt 1 ]]; then
  usage
  exit 1
fi

if [[ $# -eq 1 ]]; then
  case "$1" in
    --recreate)
      ACTION="recreate"
      ;;
    --delete-only)
      ACTION="delete-only"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
fi

delete_connector_if_exists() {
  local connector_name="$1"
  local status_code
  status_code="$(curl -s -o /dev/null -w "%{http_code}" "${CONNECT_URL}/connectors/${connector_name}")"

  if [[ "${status_code}" == "200" ]]; then
    echo "DELETE ${CONNECT_URL}/connectors/${connector_name}"
    curl -fsS -X DELETE "${CONNECT_URL}/connectors/${connector_name}" >/dev/null
    echo "deleted: ${connector_name}"
  elif [[ "${status_code}" == "404" ]]; then
    echo "skip delete (not found): ${connector_name}"
  else
    echo "커넥터 조회 실패(${status_code}): ${connector_name}" >&2
    exit 1
  fi
}

if [[ "${ACTION}" == "delete-only" || "${ACTION}" == "recreate" ]]; then
  delete_connector_if_exists "${CONNECTOR_NAME_AUCTION}"
  delete_connector_if_exists "${CONNECTOR_NAME_BID}"
fi

if [[ "${ACTION}" == "delete-only" ]]; then
  exit 0
fi

# 커넥터 등록에 필요한 DB 비밀번호가 없으면 즉시 실패 처리한다.
if [[ -z "${DEBEZIUM_PASSWORD:-}" ]]; then
  echo "DEBEZIUM_PASSWORD 가 필요합니다. infra/.env 에 설정하거나 환경변수로 지정하세요." >&2
  exit 1
fi

# connector 템플릿 JSON에 database.password를 동적으로 주입해 최종 요청 본문을 만든다.
BODY_AUCTION="$(
  jq --arg pwd "${DEBEZIUM_PASSWORD}" --arg sr "${SCHEMA_REGISTRY_URL_VALUE}" \
    '.config["database.password"] = $pwd
     | .config["value.converter.schema.registry.url"] = $sr' \
    "${CONNECTOR_JSON}"
)"
BODY_BID="$(
  jq --arg pwd "${DEBEZIUM_PASSWORD}" --arg sr "${SCHEMA_REGISTRY_URL_VALUE}" \
    '.config["database.password"] = $pwd
     | .config["value.converter.schema.registry.url"] = $sr' \
    "${CONNECTOR_JSON_BID}"
)"

# 신규 커넥터 생성 요청(이미 동일 이름이 존재하면 409 발생 가능)
echo "POST ${CONNECT_URL}/connectors"
curl -fsS -X POST "${CONNECT_URL}/connectors" \
  -H "Content-Type: application/json" \
  -d "${BODY_AUCTION}"

echo
echo "POST ${CONNECT_URL}/connectors"
curl -fsS -X POST "${CONNECT_URL}/connectors" \
  -H "Content-Type: application/json" \
  -d "${BODY_BID}"

echo