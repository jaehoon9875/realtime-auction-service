#!/usr/bin/env bash
# P3 입찰 레이턴시 성능 테스트 실행 스크립트 (GKE in-cluster k6 Job).
#
# 동작:
#   1) perf/config/baseline.env 의 고정 부하값을 읽는다.
#   2) perf/k6/*.js 를 ConfigMap으로 묶어 클러스터에 올린다(스크립트 중복 없음).
#   3) k6 Job을 auction 네임스페이스에 띄워 api-gateway-service(클러스터 내부)로 부하를 준다.
#   4) 로그를 스트리밍하며 저장하고, 요약 JSON을 perf/results/<git-sha>/ 에 격리 저장한다.
#
# 비용 주의:
#   - 부하는 클러스터 내부 트래픽이라 egress 과금이 없다.
#   - baseline.env의 VU/지속시간을 키우면 노드 오토스케일로 과금이 늘 수 있다. 신중히.
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

GIT_SHA="$(git -C "${SCRIPT_DIR}" rev-parse --short HEAD)"
GIT_DIRTY=""
git -C "${SCRIPT_DIR}" diff --quiet || GIT_DIRTY="-dirty"
RUN_ID="${GIT_SHA}${GIT_DIRTY}"
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
echo " git           : ${RUN_ID}"
echo " 설정 파일     : ${CONFIG_NAME}"
echo " 시나리오      : ${SCENARIO}"
echo " 최대 VU       : ${MAX_VUS}"
echo " 단계          : ramp-up ${RAMPUP_DUR} / 유지 ${SUSTAIN_DUR} / ramp-down ${RAMPDOWN_DUR}"
echo " 경매/입찰자   : ${AUCTION_COUNT} / ${BIDDER_COUNT}"
echo " 결과 저장     : ${RESULT_DIR}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [[ -n "${GIT_DIRTY}" ]]; then
  echo "⚠️  커밋되지 않은 변경이 있습니다(-dirty). before/after 추적을 위해 측정 전 커밋을 권장합니다."
fi

# ── 이전 리소스 정리 ──────────────────────────────────────
kubectl delete job "${JOB_NAME}" -n "${NAMESPACE}" --ignore-not-found >/dev/null 2>&1 || true

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
          image: grafana/k6:latest
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
