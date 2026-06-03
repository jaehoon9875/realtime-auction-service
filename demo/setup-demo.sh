#!/usr/bin/env bash
# 실시간 입찰 데모 준비 스크립트.
# 판매자/입찰자 계정 생성 → 경매 생성 → demo/auction-live.html에 붙여넣을 값과
# 입찰 명령(curl)을 출력한다. GKE gateway IP를 BASE_URL로 넘겨 실행한다.
#
# 사용법:
#   BASE_URL=http://<gateway-ip> ./demo/setup-demo.sh
#
# 출력된 WS Base / Auction ID / JWT를 두 브라우저 창의 auction-live.html에 입력하고,
# 출력된 입찰 명령을 터미널에서 실행하면 양쪽 화면의 현재가가 실시간 갱신된다.

set -euo pipefail

: "${BASE_URL:?BASE_URL 환경변수가 필요합니다 (예: http://<gateway-ip>)}"
BASE_URL="${BASE_URL%/}"

# jq 우선, 없으면 grep 폴백 (e2e/lib/http.sh와 동일 전략)
json_field() {
  local field="$1" json="$2"
  if command -v jq &>/dev/null; then
    echo "$json" | jq -r ".${field} // empty"
  else
    echo "$json" | grep -o "\"${field}\":\"[^\"]*\"" | head -1 | cut -d'"' -f4
  fi
}

post() { # path body [token]
  local path="$1" body="$2" token="${3:-}"
  if [ -n "$token" ]; then
    curl -s -H "Content-Type: application/json" -H "Authorization: Bearer ${token}" -d "$body" "${BASE_URL}${path}"
  else
    curl -s -H "Content-Type: application/json" -d "$body" "${BASE_URL}${path}"
  fi
}

TS=$(date +%s)
SELLER_EMAIL="demo-seller-${TS}@test.local"
BIDDER_EMAIL="demo-bidder-${TS}@test.local"
PW="DemoPass1!"

echo "▶ 계정 생성 중…"
post "/api/users/signup" "{\"email\":\"${SELLER_EMAIL}\",\"password\":\"${PW}\",\"nickname\":\"판매자\"}" >/dev/null
post "/api/users/signup" "{\"email\":\"${BIDDER_EMAIL}\",\"password\":\"${PW}\",\"nickname\":\"입찰자\"}" >/dev/null

echo "▶ 로그인 중…"
# 중첩 $() 안에서 JSON body의 중괄호가 brace expansion으로 쪼개지는 것을 피하려고
# 응답을 변수에 먼저 담은 뒤 필드를 추출한다.
SELLER_RESP=$(post "/api/users/login" "{\"email\":\"${SELLER_EMAIL}\",\"password\":\"${PW}\"}")
BIDDER_RESP=$(post "/api/users/login" "{\"email\":\"${BIDDER_EMAIL}\",\"password\":\"${PW}\"}")
SELLER_JWT=$(json_field "accessToken" "$SELLER_RESP")
BIDDER_JWT=$(json_field "accessToken" "$BIDDER_RESP")
[ -n "$SELLER_JWT" ] || { echo "✗ 판매자 로그인 실패: $SELLER_RESP"; exit 1; }
[ -n "$BIDDER_JWT" ] || { echo "✗ 입찰자 로그인 실패: $BIDDER_RESP"; exit 1; }

# 데모 도중 자동 마감되지 않도록 1시간 뒤로 설정
ENDS_AT=$(date -u -v+1H "+%Y-%m-%dT%H:%M:%SZ" 2>/dev/null \
          || date -u -d "+1 hour" "+%Y-%m-%dT%H:%M:%SZ")

echo "▶ 경매 생성 중…"
AUCTION=$(post "/api/auctions" "{\"title\":\"실시간 경매 데모\",\"startPrice\":10000,\"endsAt\":\"${ENDS_AT}\"}" "$SELLER_JWT")
AUCTION_ID=$(json_field "id" "$AUCTION")
[ -n "$AUCTION_ID" ] || { echo "✗ 경매 생성 실패: $AUCTION"; exit 1; }

# ── 파이프라인 워밍업 ──────────────────────────────────────────────────────────
# Debezium logical replication은 한동안 트래픽이 없던(idle) Postgres에서 첫 변경 캡처가
# 수십 초~분까지 지연될 수 있다. 워밍업 입찰을 한 번 넣고 currentPrice에 반영될 때까지
# 기다려두면, 이후 녹화용 입찰은 ~2초 내로 반영되어 데모가 매끄럽다.
echo "▶ 파이프라인 워밍업(첫 입찰 반영 대기, 최대 ~2분)…"
post "/api/bids" "{\"auctionId\":\"${AUCTION_ID}\",\"amount\":10500}" "$BIDDER_JWT" >/dev/null
WARM_OK=false
for i in $(seq 1 60); do
  RESP=$(curl -s -H "Authorization: Bearer ${BIDDER_JWT}" "${BASE_URL}/api/auctions/${AUCTION_ID}" || true)
  CP=$(json_field "currentPrice" "$RESP")
  if [ -n "$CP" ] && [ "$CP" != "null" ]; then
    echo "  ✓ 워밍업 완료 (약 ${i}s, currentPrice=${CP})"; WARM_OK=true; break
  fi
  sleep 2
done
$WARM_OK || echo "  ⚠ 워밍업 미반영 — 녹화 직전 입찰을 1~2회 미리 쏴서 파이프라인을 데워주세요."

# WS Base는 http→ws / https→wss 로 치환
WS_BASE=$(echo "$BASE_URL" | sed -e 's#^http://#ws://#' -e 's#^https://#wss://#')

cat <<EOF

────────────────────────────────────────────────────────────
✅ 데모 준비 완료

[ auction-live.html 에 입력할 값 ]
  WebSocket Base : ${WS_BASE}
  Auction ID     : ${AUCTION_ID}
  JWT Token      : ${BIDDER_JWT}

  → demo/auction-live.html 을 브라우저 2개 창에 띄우고 위 3개 값 입력 후 [연결]
    (한 창에서 입력하면 localStorage에 저장되어 두 번째 창에서도 자동 채워짐)

[ 녹화용 연속 입찰 — 11,000원부터 1,000원씩 8회, 3초 간격 ]
  화면 녹화를 시작한 뒤 아래를 실행하세요. 각 입찰은 약 2초 후 양쪽 창의
  현재가가 동시에 초록색으로 깜빡이며 갱신됩니다. (연속 트래픽이라 지연이 안정적)
  for a in 11000 12000 13000 14000 15000 16000 17000 18000; do
    curl -s -X POST "${BASE_URL}/api/bids" \\
      -H "Content-Type: application/json" \\
      -H "Authorization: Bearer ${BIDDER_JWT}" \\
      -d "{\\"auctionId\\":\\"${AUCTION_ID}\\",\\"amount\\":\$a}" >/dev/null
    echo "입찰 \$a 전송"; sleep 3
  done
────────────────────────────────────────────────────────────
EOF
