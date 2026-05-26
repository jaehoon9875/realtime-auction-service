#!/usr/bin/env bash

PASS=0
FAIL=0

assert_status() {
  local name="$1" expected="$2" actual="$3"
  if [ "$actual" -eq "$expected" ]; then
    echo "  [PASS] ${name}"
    PASS=$((PASS + 1))
  else
    echo "  [FAIL] ${name} — expected HTTP ${expected}, got ${actual}"
    FAIL=$((FAIL + 1))
  fi
}

assert_contains() {
  local name="$1" pattern="$2" actual="$3"
  if echo "$actual" | grep -q "$pattern"; then
    echo "  [PASS] ${name}"
    PASS=$((PASS + 1))
  else
    echo "  [FAIL] ${name} — '${pattern}'이 응답에 없음"
    echo "         응답: ${actual}"
    FAIL=$((FAIL + 1))
  fi
}

assert_not_empty() {
  local name="$1" actual="$2"
  if [ -n "$actual" ] && [ "$actual" != "null" ]; then
    echo "  [PASS] ${name}"
    PASS=$((PASS + 1))
  else
    echo "  [FAIL] ${name} — 값이 비어있음"
    FAIL=$((FAIL + 1))
  fi
}
