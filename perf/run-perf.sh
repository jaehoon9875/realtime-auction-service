#!/usr/bin/env bash
# P3 입찰 레이턴시 성능 테스트 실행 스크립트 (GKE in-cluster k6 Job).
#
# 동작:
#   1) perf/config/baseline.env 의 고정 부하값을 읽는다.
#   2) perf/k6/*.js 를 ConfigMap으로 묶어 클러스터에 올린다(스크립트 중복 없음).
#   3) k6 Job을 auction 네임스페이스에 띄워 api-gateway-service(클러스터 내부)로 부하를 준다.
#   4) 로그를 스트리밍하며 저장하고, 요약 JSON을 perf/results/<git-sha>/ 에 격리 저장한다.
#
# 실행 주의:
#   - 부하는 클러스터 내부 트래픽으로 제한한다.
#   - baseline.env의 VU/지속시간을 변경하면 노드 오토스케일이 발생할 수 있다.
#
# 사용법:
#   ./perf/run-perf.sh              # baseline.env 기본값으로 실행
#   SCENARIO=single ./perf/run-perf.sh   # 환경변수로 일부 override(권장하진 않음, before/after 일관성 깨질 수 있음)
#   ./perf/run-perf.sh stop         # 실행 중인 Job 중단/정리

set -euo pipefail

NAMESPACE="auction"
JOB_NAME="k6-bid-latency"
CONFIGMAP_NAME="k6-perf-scripts"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# k6 런타임은 before/after 재현성을 위해 고정 버전으로 핀한다(latest 같은 이동 태그 금지).
# 로컬 검증 버전(v1.7.1)과 일치. 필요 시 K6_IMAGE로 override.
K6_IMAGE="${K6_IMAGE:-grafana/k6:1.7.1}"

# ── stop 처리 ──────────────────────────────────────────────
if [[ "${1:-}" == "stop" ]]; then
  kubectl delete job "${JOB_NAME}" -n "${NAMESPACE}" --ignore-not-found
  kubectl delete configmap "${CONFIGMAP_NAME}" -n "${NAMESPACE}" --ignore-not-found
  echo ">>> 정리 완료"
  exit 0
fi

# ── 설정 로드 ──────────────────────────────────────────────
# 기본은 baseline.env. 스모크 등 다른 설정은 PERF_CONFIG로 지정:
#   PERF_CONFIG=perf/config/smoke.env ./perf/run-perf.sh
CONFIG_FILE="${PERF_CONFIG:-${SCRIPT_DIR}/config/baseline.env}"
if [[ ! -f "${CONFIG_FILE}" ]]; then
  echo "오류: 설정 파일을 찾을 수 없습니다: ${CONFIG_FILE}"; exit 1
fi
# shellcheck disable=SC1090
source "${CONFIG_FILE}"
CONFIG_NAME="$(basename "${CONFIG_FILE}" .env)"

SCRIPT_SHA="$(git -C "${SCRIPT_DIR}" rev-parse --short HEAD)"
SCRIPT_DIRTY=""
# worktree(미스테이징)와 index(스테이징) 양쪽을 검사해야 실행 자산 변경 여부가 로그에 반영된다.
if ! git -C "${SCRIPT_DIR}" diff --quiet || ! git -C "${SCRIPT_DIR}" diff --cached --quiet; then
  SCRIPT_DIRTY="-dirty"
fi

# 결과는 로컬 실행 자산이 아니라 실제 측정 대상인 bid-service 배포 이미지 태그로 식별한다.
TARGET_IMAGE="$(kubectl get deployment/bid-service-deployment -n "${NAMESPACE}" \
  -o jsonpath='{.spec.template.spec.containers[?(@.name=="bid-service")].image}')"
TARGET_TAG="${TARGET_IMAGE##*:}"
TARGET_SHA="${TARGET_TAG#preview-}"
if [[ -z "${TARGET_IMAGE}" || -z "${TARGET_TAG}" || "${TARGET_TAG}" == "${TARGET_IMAGE}" ]]; then
  echo "오류: bid-service 배포 이미지 태그를 확인할 수 없습니다: ${TARGET_IMAGE}"; exit 1
fi
if [[ ! "${TARGET_SHA}" =~ ^[0-9a-f]{7,40}$ ]]; then
  echo "⚠️  bid-service 이미지 태그가 git SHA 형식이 아닙니다: ${TARGET_TAG}"
fi

RUN_ID="${TARGET_SHA}"
# baseline이 아닌 설정(스모크 등)의 결과가 본 측정과 섞이지 않도록 RUN_ID에 표시.
if [[ "${CONFIG_NAME}" != "baseline" ]]; then
  RUN_ID="${RUN_ID}-${CONFIG_NAME}"
fi
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RESULT_DIR="${SCRIPT_DIR}/results/${RUN_ID}"
mkdir -p "${RESULT_DIR}"
LOG_FILE="${RESULT_DIR}/k6-${TIMESTAMP}.log"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " P3 입찰 레이턴시 테스트"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " 측정 대상     : ${TARGET_IMAGE}"
echo " 결과 ID       : ${RUN_ID}"
echo " 실행 자산 git : ${SCRIPT_SHA}${SCRIPT_DIRTY}"
echo " 설정 파일     : ${CONFIG_NAME}"
echo " 시나리오      : ${SCENARIO}"
echo " 최대 VU       : ${MAX_VUS}"
echo " 단계          : ramp-up ${RAMPUP_DUR} / 유지 ${SUSTAIN_DUR} / ramp-down ${RAMPDOWN_DUR}"
echo " 경매/입찰자   : ${AUCTION_COUNT} / ${BIDDER_COUNT}"
echo " 결과 저장     : ${RESULT_DIR}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [[ -n "${SCRIPT_DIRTY}" ]]; then
  echo "⚠️  실행 자산에 커밋되지 않은 변경이 있습니다. before/after 추적을 위해 측정 전 커밋을 권장합니다."
fi

# ── 이전 리소스 정리 ──────────────────────────────────────
# 삭제 요청 후 완전 종료까지 대기한다. terminating 중인 동명 Job과 다음 apply가 충돌하지 않도록.
kubectl delete job "${JOB_NAME}" -n "${NAMESPACE}" --ignore-not-found >/dev/null 2>&1 || true
kubectl wait --for=delete job/"${JOB_NAME}" -n "${NAMESPACE}" --timeout=60s >/dev/null 2>&1 || true

# ── ConfigMap: perf/k6/*.js 를 그대로 주입(중복 없음) ─────
echo ">>> ConfigMap 생성..."
kubectl create configmap "${CONFIGMAP_NAME}" \
  --from-file="${SCRIPT_DIR}/k6/bid-latency.js" \
  --from-file="${SCRIPT_DIR}/k6/auth.js" \
  -n "${NAMESPACE}" \
  --dry-run=client -o yaml | kubectl apply -f -

# ── Job 생성 ───────────────────────────────────────────────
echo ">>> Job '${JOB_NAME}' 생성..."
kubectl apply -f - <<EOF
apiVersion: batch/v1
kind: Job
metadata:
  name: ${JOB_NAME}
  namespace: ${NAMESPACE}
  labels:
    app.kubernetes.io/name: k6
    app.kubernetes.io/part-of: realtime-auction-service
spec:
  ttlSecondsAfterFinished: 300
  backoffLimit: 0
  template:
    metadata:
      labels:
        app: k6
    spec:
      restartPolicy: Never
      containers:
        - name: k6
          image: ${K6_IMAGE}
          args: ["run", "/scripts/bid-latency.js"]
          env:
            - { name: BASE_URL,     value: "http://api-gateway-service" }
            - { name: MAX_VUS,      value: "${MAX_VUS}" }
            - { name: SLEEP,        value: "${SLEEP}" }
            - { name: RAMPUP_DUR,   value: "${RAMPUP_DUR}" }
            - { name: SUSTAIN_DUR,  value: "${SUSTAIN_DUR}" }
            - { name: RAMPDOWN_DUR, value: "${RAMPDOWN_DUR}" }
            - { name: AUCTION_COUNT, value: "${AUCTION_COUNT}" }
            - { name: BIDDER_COUNT, value: "${BIDDER_COUNT}" }
            - { name: SCENARIO,     value: "${SCENARIO}" }
            - { name: RUN_ID,       value: "${RUN_ID}" }
          volumeMounts:
            - { name: scripts, mountPath: /scripts }
          resources:
            requests: { cpu: "100m", memory: "128Mi" }
            limits:   { cpu: "300m", memory: "256Mi" }
      volumes:
        - name: scripts
          configMap:
            name: ${CONFIGMAP_NAME}
EOF

# ── Pod 대기 & 로그 스트리밍(파일 동시 저장) ──────────────
echo ">>> Pod 시작 대기..."
kubectl wait --for=condition=ready pod -l job-name="${JOB_NAME}" -n "${NAMESPACE}" --timeout=120s

echo ">>> 로그 스트리밍 (결과는 ${LOG_FILE} 에도 저장)"
echo "─────────────────────────────────────────────────────────"
kubectl logs -f job/"${JOB_NAME}" -n "${NAMESPACE}" | tee "${LOG_FILE}"

# ── 요약 JSON 추출 ─────────────────────────────────────────
SUMMARY_FILE="${RESULT_DIR}/summary-${TIMESTAMP}.json"
awk '/===SUMMARY_JSON_START===/{f=1;next} /===SUMMARY_JSON_END===/{f=0} f' "${LOG_FILE}" > "${SUMMARY_FILE}" || true

if [[ -s "${SUMMARY_FILE}" ]]; then
  echo ">>> 요약 JSON 저장: ${SUMMARY_FILE}"
else
  echo "⚠️  요약 JSON을 추출하지 못했습니다. 전체 로그(${LOG_FILE})를 확인하세요."
fi

echo ">>> 완료. 산출물 디렉토리: ${RESULT_DIR}"
