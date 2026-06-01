#!/usr/bin/env bash
set -euo pipefail

: "${ARGOCD_REVISION:?ARGOCD_REVISION 환경변수가 필요합니다}"

ARGOCD_NAMESPACE="${ARGOCD_NAMESPACE:-argocd}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-600}"
WAIT_INTERVAL_SECONDS="${WAIT_INTERVAL_SECONDS:-10}"
DEADLINE=$((SECONDS + WAIT_TIMEOUT_SECONDS))

if [[ "$(kubectl auth can-i get applications.argoproj.io -n "$ARGOCD_NAMESPACE")" != "yes" ]]; then
  echo "GitHub Actions 서비스 계정에 argocd Application 조회 권한이 없습니다."
  echo "infra/argocd/project.yaml 적용 후 App-of-Apps가 github-actions-wait-rbac.yaml을 동기화했는지 확인하세요."
  exit 1
fi

wait_for_wave() {
  local wave="$1"
  shift
  local apps=("$@")

  echo "ArgoCD wave ${wave} 대기: ${apps[*]}"

  while (( SECONDS < DEADLINE )); do
    local all_ready=true
    local app

    for app in "${apps[@]}"; do
      local status
      if ! status=$(kubectl get application.argoproj.io "$app" \
          -n "$ARGOCD_NAMESPACE" \
          -o jsonpath='{.status.sync.status}{"\t"}{.status.health.status}{"\t"}{.status.sync.revision}{"\t"}{.status.operationState.phase}' \
          2>/dev/null); then
        echo "  ${app}: Application 조회 대기 중"
        all_ready=false
        continue
      fi

      local sync_status health_status revision operation_phase
      IFS=$'\t' read -r sync_status health_status revision operation_phase <<< "$status"

      if [[ "$sync_status" == "Synced" &&
            "$health_status" == "Healthy" &&
            "$revision" == "$ARGOCD_REVISION" ]]; then
        echo "  ${app}: Synced + Healthy (${revision:0:7})"
      else
        echo "  ${app}: sync=${sync_status:-Unknown}, health=${health_status:-Unknown}, revision=${revision:0:7}, operation=${operation_phase:-None}"
        all_ready=false
      fi
    done

    if [[ "$all_ready" == "true" ]]; then
      return 0
    fi

    sleep "$WAIT_INTERVAL_SECONDS"
  done

  echo "ArgoCD Application 대기 시간(${WAIT_TIMEOUT_SECONDS}s)을 초과했습니다."
  kubectl get applications.argoproj.io -n "$ARGOCD_NAMESPACE" \
    -o custom-columns='NAME:.metadata.name,SYNC:.status.sync.status,HEALTH:.status.health.status,REVISION:.status.sync.revision,OPERATION:.status.operationState.phase'
  return 1
}

# 각 Application은 auto-sync되므로 실제 적용은 병렬로 진행될 수 있다.
# 여기서는 의존성 순서대로 상태를 검증해 실패 지점을 명확히 남긴다.
wait_for_wave "0" external-secrets-sa kafka
wait_for_wave "1" external-secrets-config schema-registry
wait_for_wave "2" debezium
wait_for_wave "3" auction-streams
wait_for_wave "4" user-service auction-service bid-service
wait_for_wave "5" api-gateway
wait_for_wave "6" notification-service

echo "배포 대상 ArgoCD Application이 모두 Synced + Healthy 상태입니다: ${ARGOCD_REVISION:0:7}"
