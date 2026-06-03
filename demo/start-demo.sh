#!/usr/bin/env bash
# 데모 UI 실행기.
# HTTP 서버를 백그라운드로 띄우고 브라우저에서 로그인 페이지를 연다.
#
# 사용법:
#   ./demo/start-demo.sh          # 기본 포트 8888
#   ./demo/start-demo.sh 9000     # 포트 지정

set -euo pipefail

PORT="${1:-8888}"
DEMO_DIR="$(cd "$(dirname "$0")" && pwd)"
URL="http://localhost:${PORT}/login.html"

# 이미 해당 포트에서 서버가 실행 중이면 종료
if lsof -ti tcp:"$PORT" &>/dev/null; then
  echo "포트 ${PORT} 사용 중 → 기존 프로세스 종료"
  lsof -ti tcp:"$PORT" | xargs kill -9 2>/dev/null || true
  sleep 0.5
fi

echo "데모 서버 시작 중 (포트 ${PORT})…"
python3 -m http.server "$PORT" --directory "$DEMO_DIR" \
  --bind 127.0.0.1 \
  > /tmp/demo-server.log 2>&1 &
SERVER_PID=$!

# 서버 응답 대기 (최대 3초)
for i in $(seq 1 6); do
  if curl -s -o /dev/null "http://localhost:${PORT}/login.html"; then
    break
  fi
  sleep 0.5
done

echo "✓ 서버 실행 중 (PID ${SERVER_PID}) → ${URL}"
echo "  종료하려면: kill ${SERVER_PID}"
echo "  로그 확인:  tail -f /tmp/demo-server.log"
echo ""

# 브라우저 열기 (macOS)
open "$URL"
