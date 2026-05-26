#!/usr/bin/env bash
# E2E Smoke Test
# 사용법: BASE_URL=http://<gateway-ip> ./e2e/smoke.sh
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=lib/assert.sh
source "${SCRIPT_DIR}/lib/assert.sh"
# shellcheck source=lib/http.sh
source "${SCRIPT_DIR}/lib/http.sh"

: "${BASE_URL:?BASE_URL 환경변수가 필요합니다 (예: http://<gateway-ip>)}"

echo "========================================"
echo " Smoke Test  →  ${BASE_URL}"
echo "========================================"
echo ""

run_scenario() {
  local title="$1" file="$2"
  echo "[ ${title} ]"
  # shellcheck source=/dev/null
  source "${file}"
  echo ""
}

run_scenario "01. Health Check"  "${SCRIPT_DIR}/scenarios/01_health.sh"
run_scenario "02. Happy Path"    "${SCRIPT_DIR}/scenarios/02_happy_path.sh"

echo "========================================"
echo " 결과: PASS ${PASS}  FAIL ${FAIL}"
echo "========================================"

[ "${FAIL}" -eq 0 ]
