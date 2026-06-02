#!/usr/bin/env bash
# 성능 테스트 전후 클러스터 상태를 동일한 기준으로 확인하는 읽기 전용 스크립트.

set -euo pipefail

NAMESPACE="auction"
KAFKA_BOOTSTRAP="auction-kafka-kafka-bootstrap:9092"
LAG_RECHECK_SECONDS=10
KAFKA_POD="$(kubectl get pods -n "${NAMESPACE}" \
  -l strimzi.io/name=auction-kafka-kafka \
  -o jsonpath='{.items[0].metadata.name}')"
GATEWAY_IP="$(kubectl get service api-gateway-service -n "${NAMESPACE}" \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}')"

section() {
  printf '\n--- %s ---\n' "$1"
}

print_consumer_lag() {
  for group in auction-streams notification-service; do
    printf '\n### %s ###\n' "${group}"
    kubectl exec -n "${NAMESPACE}" "${KAFKA_POD}" -- \
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
curl --fail --silent --show-error --max-time 10 "http://${GATEWAY_IP}/actuator/health"
printf '\n'

section "debezium connectors"
for connector in auction-outbox-connector bid-outbox-connector; do
  printf '\n### %s ###\n' "${connector}"
  kubectl exec -n "${NAMESPACE}" deployment/debezium-deployment -c debezium -- \
    curl --fail --silent --show-error "http://localhost:8083/connectors/${connector}/status"
  printf '\n'
done

section "kafka consumer lag (sample 1)"
print_consumer_lag

section "kafka consumer lag (sample 2 after ${LAG_RECHECK_SECONDS}s)"
sleep "${LAG_RECHECK_SECONDS}"
print_consumer_lag

section "warning events"
kubectl get events -n "${NAMESPACE}" --field-selector type=Warning --sort-by='.lastTimestamp'
