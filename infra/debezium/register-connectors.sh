#!/usr/bin/env bash
# Kafka Connect(Debezium 컨테이너)에 auction-outbox 커넥터를 등록한다.
# database.password 는 저장소에 넣지 않고 DEBEZIUM_PASSWORD 환경변수로 주입한다.
# 주의: 이 스크립트는 "최초 1회 등록" 용도다.
# 동일 이름의 커넥터가 이미 있으면 POST /connectors 는 409 Conflict 를 반환한다.
# 재등록/갱신이 필요하면 docs/debezium-connector.md 의 관리 절차(DELETE 후 재등록)를 따른다.
set -euo pipefail

# 스크립트 위치를 기준으로 커넥터 JSON 경로를 계산한다.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONNECTOR_JSON="${SCRIPT_DIR}/connectors/auction-outbox-connector.json"

# infra/.env가 있으면 자동 로드해서 환경변수를 세팅한다.
if [[ -f "${SCRIPT_DIR}/../.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${SCRIPT_DIR}/../.env"
  set +a
fi

# Kafka Connect 주소를 환경변수 또는 기본값(localhost:DEBEZIUM_PORT)으로 결정한다.
CONNECT_URL="${KAFKA_CONNECT_URL:-http://localhost:${DEBEZIUM_PORT:-8083}}"

# 커넥터 등록에 필요한 DB 비밀번호가 없으면 즉시 실패 처리한다.
if [[ -z "${DEBEZIUM_PASSWORD:-}" ]]; then
  echo "DEBEZIUM_PASSWORD 가 필요합니다. infra/.env 에 설정하거나 환경변수로 지정하세요." >&2
  exit 1
fi

# connector 템플릿 JSON에 database.password를 동적으로 주입해 최종 요청 본문을 만든다.
BODY="$(jq --arg pwd "${DEBEZIUM_PASSWORD}" '.config["database.password"] = $pwd' "${CONNECTOR_JSON}")"

# 신규 커넥터 생성 요청(이미 동일 이름이 존재하면 409 발생 가능)
echo "POST ${CONNECT_URL}/connectors"
curl -fsS -X POST "${CONNECT_URL}/connectors" \
  -H "Content-Type: application/json" \
  -d "${BODY}"

echo