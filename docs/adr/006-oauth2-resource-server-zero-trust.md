---
status: "accepted"
date: 2026-05-19
decision-makers: jaehoon9875
---

# OAuth2 Resource Server로 Zero Trust JWT 인증 전환

## Context and Problem Statement

초기 구현에서 JWT 검증은 API Gateway에서만 수행하고, 검증 결과를 `X-User-Id` 헤더로 하위 서비스에 전달했습니다.
하위 서비스(auction, bid, user)는 이 헤더를 신뢰하여 SecurityContext에 등록하는 방식이었습니다.

**MSA 환경에서 각 서비스가 JWT를 어떻게 검증해야 하는가?**

## Decision Drivers

- Gateway 우회 시 `X-User-Id` 헤더를 임의로 조작할 수 있는 보안 취약점 존재
- 동일한 JWT 검증 로직이 각 서비스에 분산되어야 하나, 중복 구현을 최소화해야 함
- M8 GKE 배포에서 Istio 서비스 메시 도입 시 자연스럽게 연계 가능해야 함

## Considered Options

- Gateway 집중 검증 + X-User-Id 헤더 전달 (기존 방식)
- OAuth2 Resource Server — Gateway 1차 방어 + 각 서비스 독립 검증 (Zero Trust)
- Istio RequestAuthentication — 서비스 메시 레벨에서 JWT 검증

## Decision Outcome

Chosen option: **"OAuth2 Resource Server — Gateway 1차 방어 + 각 서비스 독립 검증"**, because Gateway에서 유효하지 않은 토큰을 조기에 차단하여 불필요한 트래픽을 줄이면서, 각 서비스가 `spring-security-oauth2-resource-server`로 JWT를 독립 검증하여 Gateway 우회 시에도 스스로 방어할 수 있기 때문.

```text
[기존]
클라이언트 → Gateway(JWT 검증 + X-User-Id 주입) → 각 서비스(헤더만 읽음)

[변경]
클라이언트 → Gateway(1차: 만료·위조 토큰 조기 차단, Authorization 헤더 그대로 전달)
           → 각 서비스(2차: JWKS 엔드포인트로 공개키 가져와 JWT 직접 검증)
```

user-service가 `/.well-known/jwks.json`으로 RSA 공개키를 노출하고,
auction-service·bid-service는 이 엔드포인트를 통해 공개키를 가져와 JWT를 검증한다.

### Consequences

- Good, because Gateway 우회 시에도 각 서비스가 독립적으로 JWT 검증하여 방어
- Good, because `spring-security-oauth2-resource-server` 표준 라이브러리 활용으로 검증 코드 중복 없음
- Good, because M8에서 Istio `RequestAuthentication` 도입 시 이 코드가 defense-in-depth로 유지됨
- Bad, because 각 서비스 기동 시 user-service의 JWKS 엔드포인트에 의존 (user-service 장애 시 공개키 갱신 불가, 단 캐싱으로 완화)

### Confirmation

`/.well-known/jwks.json` 응답에 공개키 포함 확인.
유효한 JWT로 각 서비스 API 호출 성공, 위조·만료 JWT로 401 반환 확인.

## Pros and Cons of the Options

### Gateway 집중 검증 + X-User-Id 헤더 전달 (기존)

- Good, because 서비스별 JWT 검증 코드 불필요, 구현이 단순
- Bad, because Gateway 우회 또는 내부 네트워크 공격 시 `X-User-Id` 헤더 조작 가능
- Bad, because `X-Internal-Request-Token` 단일 방어선에 의존

### OAuth2 Resource Server (채택)

- Good, because 각 서비스가 스스로 JWT를 검증하므로 Gateway 우회 시에도 방어
- Good, because Spring Security 표준 방식으로 보일러플레이트 없음
- Neutral, because JWKS 엔드포인트 의존성 추가되나, 공개키는 캐싱되어 매 요청마다 네트워크 호출 없음

### Istio RequestAuthentication

- Good, because 애플리케이션 코드 수정 없이 서비스 메시 레벨에서 JWT 검증
- Bad, because Kubernetes + Istio 환경에서만 동작하여 로컬 개발 환경에서 적용 불가
- Bad, because M8 이전 단계에서 적용 불가능
