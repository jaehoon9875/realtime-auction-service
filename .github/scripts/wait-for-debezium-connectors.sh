#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-auction}"
DEBEZIUM_DEPLOYMENT="${DEBEZIUM_DEPLOYMENT:-deploy/debezium-deployment}"
DEBEZIUM_CONTAINER="${DEBEZIUM_CONTAINER:-debezium}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-300}"
WAIT_INTERVAL_SECONDS="${WAIT_INTERVAL_SECONDS:-5}"
RECOVER_FAILED_CONNECTORS="${RECOVER_FAILED_CONNECTORS:-true}"

CONNECTORS=("$@")
if [[ "${#CONNECTORS[@]}" -eq 0 ]]; then
  CONNECTORS=(auction-outbox-connector bid-outbox-connector)
fi

DEADLINE=$((SECONDS + WAIT_TIMEOUT_SECONDS))

connect_get() {
  local path="$1"
  kubectl exec -n "$NAMESPACE" "$DEBEZIUM_DEPLOYMENT" -c "$DEBEZIUM_CONTAINER" -- \
    curl -sf "http://localhost:8083${path}"
}

connect_post() {
  local path="$1"
  kubectl exec -n "$NAMESPACE" "$DEBEZIUM_DEPLOYMENT" -c "$DEBEZIUM_CONTAINER" -- \
    curl -sf -X POST "http://localhost:8083${path}" >/dev/null
}

connector_ready() {
  local name="$1"
  local status_json="$2"

  jq -e '
    .connector.state == "RUNNING"
    and (.tasks | length) > 0
    and all(.tasks[]; .state == "RUNNING")
  ' >/dev/null <<< "$status_json"
}

connector_failed() {
  local status_json="$1"

  jq -e '
    .connector.state == "FAILED"
    or any(.tasks[]?; .state == "FAILED")
  ' >/dev/null <<< "$status_json"
}

for connector in "${CONNECTORS[@]}"; do
  echo "Debezium connector 대기: ${connector}"

  while (( SECONDS < DEADLINE )); do
    if ! status_json="$(connect_get "/connectors/${connector}/status")"; then
      echo "  ${connector}: 상태 조회 실패"
      sleep "$WAIT_INTERVAL_SECONDS"
      continue
    fi

    if connector_ready "$connector" "$status_json"; then
      echo "  ${connector}: connector/task RUNNING"
      break
    fi

    if [[ "$RECOVER_FAILED_CONNECTORS" == "true" ]] && connector_failed "$status_json"; then
      echo "  ${connector}: FAILED task 감지, failed task 재시작"
      connect_post "/connectors/${connector}/restart?includeTasks=true&onlyFailed=true"
    else
      state_summary="$(jq -r '[.connector.state, (.tasks[]?.state)] | join("/")' <<< "$status_json")"
      echo "  ${connector}: RUNNING 대기 중 (${state_summary})"
    fi

    sleep "$WAIT_INTERVAL_SECONDS"
  done

  if (( SECONDS >= DEADLINE )); then
    echo "Debezium connector 대기 시간(${WAIT_TIMEOUT_SECONDS}s)을 초과했습니다: ${connector}"
    connect_get "/connectors/${connector}/status" || true
    exit 1
  fi
done

echo "필수 Debezium connector가 모두 RUNNING 상태입니다."
