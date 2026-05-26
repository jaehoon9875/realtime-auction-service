#!/usr/bin/env bash
# S2: 회원가입 → 로그인 → 경매 생성 → 입찰 → currentPrice 검증

# 테스트 실행마다 고유 이메일 생성 (동시 실행 충돌 방지)
TS=$(date +%s)
USER_A_EMAIL="smoke-a-${TS}@test.local"
USER_A_PASSWORD="SmokeTest1!"
USER_B_EMAIL="smoke-b-${TS}@test.local"
USER_B_PASSWORD="SmokeTest2!"

# ── 회원가입 ──────────────────────────────────────────────────────────────────

http_post "/api/users/signup" \
  "{\"email\":\"${USER_A_EMAIL}\",\"password\":\"${USER_A_PASSWORD}\",\"nickname\":\"SmokeUserA\"}"
assert_status "User A 회원가입" 201 "$HTTP_STATUS"

http_post "/api/users/signup" \
  "{\"email\":\"${USER_B_EMAIL}\",\"password\":\"${USER_B_PASSWORD}\",\"nickname\":\"SmokeUserB\"}"
assert_status "User B 회원가입" 201 "$HTTP_STATUS"

# ── 로그인 → JWT 추출 ─────────────────────────────────────────────────────────

http_post "/api/users/login" \
  "{\"email\":\"${USER_A_EMAIL}\",\"password\":\"${USER_A_PASSWORD}\"}"
assert_status "User A 로그인" 200 "$HTTP_STATUS"
JWT_A=$(json_field "accessToken" "$HTTP_BODY")
assert_not_empty "User A accessToken 발급" "$JWT_A"

http_post "/api/users/login" \
  "{\"email\":\"${USER_B_EMAIL}\",\"password\":\"${USER_B_PASSWORD}\"}"
assert_status "User B 로그인" 200 "$HTTP_STATUS"
JWT_B=$(json_field "accessToken" "$HTTP_BODY")
assert_not_empty "User B accessToken 발급" "$JWT_B"

# ── 경매 생성 (User A) ────────────────────────────────────────────────────────

# endsAt: 90초 후 (스케줄러 30초 주기 고려, 여유 있게 설정)
ENDS_AT=$(date -u -d "+90 seconds" "+%Y-%m-%dT%H:%M:%SZ" 2>/dev/null \
          || date -u -v+90S "+%Y-%m-%dT%H:%M:%SZ")  # macOS 폴백

http_post "/api/auctions" \
  "{\"title\":\"Smoke Test Auction ${TS}\",\"startPrice\":10000,\"endsAt\":\"${ENDS_AT}\"}" \
  "$JWT_A"
assert_status "경매 생성" 201 "$HTTP_STATUS"
AUCTION_ID=$(json_field "id" "$HTTP_BODY")
assert_not_empty "auctionId 반환" "$AUCTION_ID"

# Debezium CDC → Kafka Streams State Store 반영 대기
sleep 5

# ── 입찰 (User B) ─────────────────────────────────────────────────────────────

# 재시도: State Store에 auction 정보가 아직 없을 수 있음
BID_OK=false
for i in 1 2 3; do
  http_post "/api/bids" \
    "{\"auctionId\":\"${AUCTION_ID}\",\"amount\":15000}" \
    "$JWT_B"
  if [ "$HTTP_STATUS" -eq 201 ]; then
    BID_OK=true
    break
  fi
  echo "  입찰 재시도 ${i}/3 (HTTP ${HTTP_STATUS})..."
  sleep 3
done

if $BID_OK; then
  assert_status "입찰 성공" 201 "$HTTP_STATUS"
else
  assert_status "입찰 성공 (3회 시도 모두 실패)" 201 "$HTTP_STATUS"
fi

# State Store 갱신 대기
sleep 3

# ── currentPrice 검증 ─────────────────────────────────────────────────────────

http_get "/api/auctions/${AUCTION_ID}" "$JWT_B"
assert_status "경매 조회" 200 "$HTTP_STATUS"
assert_contains "currentPrice=15000 반영" '"currentPrice":15000' "$HTTP_BODY"
