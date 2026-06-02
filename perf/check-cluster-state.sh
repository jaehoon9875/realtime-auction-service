#!/usr/bin/env bash
# 성능 테스트 전후 클러스터 상태를 동일한 기준으로 확인하는 읽기 전용 스크립트.

set -uo pipefail

NAMESPACE="auction"
KAFKA_BOOTSTRAP="auction-kafka-kafka-bootstrap:9092"
LAG_RECHECK_SECONDS=10
KAFKA_POD="$(kubectl get pods -n "${NAMESPACE}" \
  -l strimzi.io/name=auction-kafka-kafka \
  -o jsonpath='{.items[0].metadata.name}')"
# ip 우선, 없으면 hostname 시도 (클라우드 환경에 따라 둘 중 하나만 제공될 수 있음)
GATEWAY_ADDR="$(kubectl get service api-gateway-service -n "${NAMESPACE}" \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}{.status.loadBalancer.ingress[0].hostname}')"

section() {
  printf '\n--- %s ---\n' "$1"
}

# 섹션별 실패를 경고로 처리하고 후속 섹션 수집을 계속 진행한다.
run_section() {
  local name="$1"
  shift
  if ! "$@"; then
    echo "경고: [${name}] 섹션 실패 — 후속 섹션을 계속 수집합니다."
  fi
}

print_consumer_lag() {
  for group in auction-streams notification-service; do
    printf '\n### %s ###\n' "${group}"
    run_section "kafka-lag-${group}" kubectl exec -n "${NAMESPACE}" "${KAFKA_POD}" -- \
      bin/kafka-consumer-groups.sh \
      --bootstrap-server "${KAFKA_BOOTSTRAP}" \
      --describe \
      --group "${group}"
  done
}

section "timestamp"
date -u '+%Y-%m-%dT%H:%M:%SZ'

section "kubectl context"
kubectl config current-context

section "pods"
kubectl get pods -n "${NAMESPACE}" -o wide

section "deployment images"
kubectl get deployments -n "${NAMESPACE}" \
  -o custom-columns='NAME:.metadata.name,READY:.status.readyReplicas,IMAGE:.spec.template.spec.containers[0].image'

section "jobs"
kubectl get jobs -n "${NAMESPACE}"

section "nodes"
kubectl get nodes -L cloud.google.com/gke-nodepool,cloud.google.com/gke-spot

section "node usage"
kubectl top nodes || echo "경고: node metrics 조회 실패"

section "pod usage"
kubectl top pods -n "${NAMESPACE}" --containers || echo "경고: pod metrics 조회 실패"

section "gateway health"
if [[ -n "${GATEWAY_ADDR}" ]]; then
  run_section "gateway-health" curl --fail --silent --show-error --max-time 10 "http://${GATEWAY_ADDR}/actuator/health"
else
  echo "경고: api-gateway-service의 LoadBalancer 주소(ip/hostname)를 찾지 못했습니다."
fi
printf '\n'

section "debezium connectors"
for connector in auction-outbox-connector bid-outbox-connector; do
  printf '\n### %s ###\n' "${connector}"
  run_section "debezium-${connector}" kubectl exec -n "${NAMESPACE}" deployment/debezium-deployment -c debezium -- \
    curl --fail --silent --show-error "http://localhost:8083/connectors/${connector}/status"
  printf '\n'
done

section "kafka consumer lag (sample 1)"
print_consumer_lag

section "kafka consumer lag (sample 2 after ${LAG_RECHECK_SECONDS}s)"
sleep "${LAG_RECHECK_SECONDS}"
print_consumer_lag

section "warning events"
run_section "warning-events" kubectl get events -n "${NAMESPACE}" --field-selector type=Warning --sort-by='.lastTimestamp'
