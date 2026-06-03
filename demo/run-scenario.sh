#!/usr/bin/env bash
# 입찰 시나리오 실행기.
# setup-demo.sh가 출력한 값(BASE, AUCTION_ID, JWT)을 환경변수로 넘겨 실행한다.
#
# 사용법:
#   BASE=http://34.64.61.251 AUCTION_ID=<uuid> JWT=<token> ./demo/run-scenario.sh <scenario>
#
# 시나리오 목록:
#   sequential   — 11,000→18,000 원, 1,000원씩, 3초 간격  (기본 데모용)
#   rapid        — 11,000→50,000 원, 1,000원씩, 0.5초 간격 (실시간성 강조)
#   compete      — 입찰자 2명이 교차 입찰 (OUTBID 이벤트 발생)
#   burst        — 10명이 동시에 서로 다른 금액으로 입찰 (동시성 테스트)
#   help         — 이 도움말 출력

set -euo pipefail

SCENARIO="${1:-help}"
BASE="${BASE:-}"
AUCTION_ID="${AUCTION_ID:-}"
JWT="${JWT:-}"

# ── 헬퍼 ─────────────────────────────────────────────────────────────────────

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log()  { echo -e "${CYAN}[$(date +%T)]${NC} $*"; }
ok()   { echo -e "${GREEN}[$(date +%T)] ✓${NC} $*"; }
warn() { echo -e "${YELLOW}[$(date +%T)] ⚠${NC} $*"; }
err()  { echo -e "${RED}[$(date +%T)] ✗${NC} $*"; }

bid() { # amount [label]
  local amount="$1" label="${2:-${1}원}"
  local resp
  resp=$(curl -s -X POST "${BASE}/api/bids" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${JWT}" \
    -d "{\"auctionId\":\"${AUCTION_ID}\",\"amount\":${amount}}")
  local status
  status=$(echo "$resp" | jq -r '.status // "ERROR"' 2>/dev/null || echo "ERROR")
  if [ "$status" = "ACCEPTED" ]; then
    ok "입찰 ${label} → ACCEPTED"
  else
    warn "입찰 ${label} → ${status}  ($(echo "$resp" | jq -r '.message // ""' 2>/dev/null))"
  fi
}

bid_as() { # jwt amount [label]  — 다중 입찰자용
  local token="$1" amount="$2" label="${3:-${2}원}"
  local resp
  resp=$(curl -s -X POST "${BASE}/api/bids" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${token}" \
    -d "{\"auctionId\":\"${AUCTION_ID}\",\"amount\":${amount}}")
  local status
  status=$(echo "$resp" | jq -r '.status // "ERROR"' 2>/dev/null || echo "ERROR")
  if [ "$status" = "ACCEPTED" ]; then
    ok "[${label}] 입찰 ${amount}원 → ACCEPTED"
  else
    warn "[${label}] 입찰 ${amount}원 → ${status}"
  fi
}

make_user() { # suffix → stdout: jwt
  local ts="${1:-$(date +%s%N)}"
  local email="scenario-${ts}@test.local"
  curl -s -X POST "${BASE}/api/users/signup" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${email}\",\"password\":\"Scene1234!\",\"nickname\":\"bidder-${ts}\"}" > /dev/null || true
  curl -s -X POST "${BASE}/api/users/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${email}\",\"password\":\"Scene1234!\"}" | jq -r '.accessToken' || true
}

check_env() {
  [ -n "$BASE" ]       || { err "BASE 환경변수가 필요합니다."; exit 1; }
  [ -n "$AUCTION_ID" ] || { err "AUCTION_ID 환경변수가 필요합니다."; exit 1; }
  [ -n "$JWT" ]        || { err "JWT 환경변수가 필요합니다."; exit 1; }
}

# ── 시나리오 ──────────────────────────────────────────────────────────────────

scenario_sequential() {
  echo ""
  echo -e "${CYAN}▶ sequential: 11,000→18,000원 / 1,000원씩 / 3초 간격${NC}"
  echo "   → 브라우저 창에서 현재가가 3초마다 갱신되는 것을 확인하세요."
  echo ""
  for a in 11000 12000 13000 14000 15000 16000 17000 18000; do
    bid "$a"
    sleep 3
  done
  ok "sequential 완료"
}

scenario_rapid() {
  echo ""
  echo -e "${CYAN}▶ rapid: 11,000→50,000원 / 1,000원씩 / 0.5초 간격${NC}"
  echo "   → 현재가가 빠르게 치솟는 장면 (실시간성 강조)"
  echo ""
  for a in $(seq 11000 1000 50000); do
    bid "$a"
    sleep 0.5
  done
  ok "rapid 완료 (40회 입찰)"
}

scenario_compete() {
  echo ""
  echo -e "${CYAN}▶ compete: 입찰자 2명 교차 입찰 (OUTBID 이벤트 발생)${NC}"
  echo "   → 먼저 입찰한 사람이 밀릴 때 OUTBID 알림이 발생합니다."
  echo ""
  log "입찰자 B 계정 생성 중…"
  local JWT_B
  JWT_B=$(make_user "$(date +%s)b")
  [ -n "$JWT_B" ] && [ "$JWT_B" != "null" ] || { err "입찰자 B 계정 생성 실패"; exit 1; }
  ok "입찰자 B 준비 완료"
  echo ""

  local amounts_a=(20000 22000 24000 26000 28000)
  local amounts_b=(21000 23000 25000 27000 29000)

  for i in 0 1 2 3 4; do
    bid_as "$JWT"   "${amounts_a[$i]}" "A"
    sleep 1
    bid_as "$JWT_B" "${amounts_b[$i]}" "B"
    sleep 2
  done
  ok "compete 완료"
}

scenario_burst() {
  echo ""
  echo -e "${CYAN}▶ burst: 10명 동시 입찰 (동시성 테스트)${NC}"
  echo "   → 여러 입찰이 동시에 들어올 때 State Store의 동시성 처리 확인."
  echo ""
  log "입찰자 10명 계정 생성 중 (약 20초)…"

  local jwts=()
  for i in $(seq 1 10); do
    jwts+=("$(make_user "$(date +%s%N)_${i}")")
  done
  ok "입찰자 10명 준비 완료"

  # JWT가 유효하지 않은 계정은 제외
  local valid_jwts=()
  for jwt in "${jwts[@]}"; do
    [ -n "$jwt" ] && [ "$jwt" != "null" ] && valid_jwts+=("$jwt")
  done
  if [ "${#valid_jwts[@]}" -eq 0 ]; then
    err "유효한 JWT가 없습니다. 계정 생성에 실패했습니다."; exit 1
  fi
  [ "${#valid_jwts[@]}" -lt "${#jwts[@]}" ] && warn "일부 계정 생성 실패 (유효: ${#valid_jwts[@]}/${#jwts[@]})"
  echo ""

  log "동시 입찰 시작…"
  local base_amount=30000
  for i in "${!valid_jwts[@]}"; do
    local amount=$(( base_amount + i * 1000 ))
    bid_as "${valid_jwts[$i]}" "$amount" "bidder-$((i+1))" &
  done
  wait
  ok "burst 완료 (10명 동시)"
}

# ── 진입점 ────────────────────────────────────────────────────────────────────

case "$SCENARIO" in
  sequential) check_env; scenario_sequential ;;
  rapid)      check_env; scenario_rapid      ;;
  compete)    check_env; scenario_compete    ;;
  burst)      check_env; scenario_burst      ;;
  help|*)
    echo ""
    echo "사용법:"
    echo "  BASE=http://<gw> AUCTION_ID=<uuid> JWT=<token> ./demo/run-scenario.sh <scenario>"
    echo ""
    echo "시나리오:"
    echo "  sequential  — 11,000→18,000원, 3초 간격 (기본 데모)"
    echo "  rapid       — 11,000→50,000원, 0.5초 간격 (실시간성 강조)"
    echo "  compete     — 입찰자 2명 교차 입찰 (OUTBID 이벤트 발생)"
    echo "  burst       — 10명 동시 입찰 (동시성 테스트)"
    echo ""
    echo "팁: setup-demo.sh 실행 후 출력된 값을 그대로 사용하세요."
    echo "  BASE=http://<gateway-ip> AUCTION_ID=<uuid> JWT=<token> ./demo/run-scenario.sh rapid"
    echo ""
    ;;
esac
