#!/usr/bin/env bash
# S1: Gateway 헬스 확인

http_get "/actuator/health"
assert_status "Gateway /actuator/health 응답" 200 "$HTTP_STATUS"
assert_contains "Gateway status UP" '"status"[[:space:]]*:[[:space:]]*"UP"' "$HTTP_BODY"
