#!/usr/bin/env bash

# curl 호출 결과를 HTTP_STATUS(숫자)와 HTTP_BODY(문자열)에 저장한다
_curl() {
  local tmpbody
  tmpbody=$(mktemp)
  HTTP_STATUS=$(curl -s -o "$tmpbody" -w "%{http_code}" "$@")
  HTTP_BODY=$(cat "$tmpbody")
  rm -f "$tmpbody"
}

http_get() {
  local path="$1" token="${2:-}"
  if [ -n "$token" ]; then
    _curl -H "Authorization: Bearer ${token}" "${BASE_URL}${path}"
  else
    _curl "${BASE_URL}${path}"
  fi
}

http_post() {
  local path="$1" body="$2" token="${3:-}"
  if [ -n "$token" ]; then
    _curl -H "Content-Type: application/json" \
          -H "Authorization: Bearer ${token}" \
          -d "$body" \
          "${BASE_URL}${path}"
  else
    _curl -H "Content-Type: application/json" \
          -d "$body" \
          "${BASE_URL}${path}"
  fi
}

# jq로 JSON 필드를 추출한다. jq 없으면 grep 폴백
json_field() {
  local field="$1" json="$2"
  if command -v jq &>/dev/null; then
    echo "$json" | jq -r ".${field} // empty"
  else
    echo "$json" | grep -o "\"${field}\":\"[^\"]*\"" | cut -d'"' -f4
  fi
}
