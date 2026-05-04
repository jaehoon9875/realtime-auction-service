# 내부 서비스 인증 (Internal Service Authentication)

Gateway와 내부 서비스 간 신뢰 관계를 수립하는 Pre-shared Secret 방식에 대한 가이드입니다.

---

## 왜 필요한가

외부 클라이언트가 Gateway를 우회하여 auction-service에 직접 HTTP 요청을 보내는 것을 차단하기 위함입니다.

```
[외부 클라이언트]
    │ JWT 토큰만 소지
    ▼
[API Gateway]  ─── JWT 검증 완료 후 ───▶  X-Internal-Request-Token 헤더 주입
    │
    ▼
[auction-service]  ─── 헤더 값 검증 ───▶  일치하면 처리 / 불일치하면 403 거부
```

Gateway를 통하지 않은 요청은 `X-Internal-Request-Token` 헤더를 가질 수 없으므로 자동으로 차단됩니다.

---

## 동작 방식

1. 운영자가 무작위 문자열(시크릿)을 생성하여 Gateway와 auction-service 양쪽에 환경변수로 주입합니다.
2. Gateway는 `AddRequestHeader` 필터로 모든 auction-service 요청에 해당 값을 헤더에 추가합니다.
3. auction-service의 `InternalRequestTokenFilter`가 헤더 값을 자신이 가진 시크릿과 비교합니다.

---

## 초기 설정

### 1단계: 시크릿 값 생성

```bash
openssl rand -hex 32
# 출력 예: a3f9b2c1d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1
```

생성된 값을 복사해둡니다.

### 2단계: 환경변수 파일에 추가

`infra/.env` 파일에 생성한 값을 추가합니다 (두 서비스가 같은 값을 공유해야 합니다).

```env
# ── 내부 서비스 인증 ──────────────────────────────────────────
# Gateway → auction-service 요청 검증용 사전 공유 시크릿
# openssl rand -hex 32 로 생성. Gateway와 auction-service에 동일한 값 주입 필수.
INTERNAL_REQUEST_SECRET=<openssl rand -hex 32 으로 생성한 값>
```

> `.env` 파일은 Git에 커밋하지 않습니다. `.env.example`에 키 이름만 유지하세요.

### 3단계: Gateway 설정 확인

`services/api-gateway/src/main/resources/application.yml`의 auction-service 라우트에 이미 설정되어 있습니다.

```yaml
- id: auction-service
  filters:
    - AddRequestHeader=X-Internal-Request-Token, ${INTERNAL_REQUEST_SECRET}
```

### 4단계: auction-service 설정 확인

`services/auction-service/src/main/resources/application.yml`에 이미 설정되어 있습니다.

```yaml
app:
  security:
    internal-request-header-name: ${INTERNAL_REQUEST_HEADER_NAME:X-Internal-Request-Token}
    internal-request-secret: ${INTERNAL_REQUEST_SECRET:}
```

`INTERNAL_REQUEST_HEADER_NAME`은 기본값(`X-Internal-Request-Token`)을 사용하므로 별도 설정이 불필요합니다.

---

## K8s 환경에서의 설정

로컬 `.env` 대신 Kubernetes Secret을 사용합니다.

```bash
kubectl create secret generic internal-auth-secret \
  --from-literal=INTERNAL_REQUEST_SECRET=<생성한_값>
```

Gateway와 auction-service Deployment 양쪽에 동일한 Secret을 마운트합니다.

```yaml
# Deployment spec.containers.env
- name: INTERNAL_REQUEST_SECRET
  valueFrom:
    secretKeyRef:
      name: internal-auth-secret
      key: INTERNAL_REQUEST_SECRET
```

---

## 검증

서비스 기동 후 아래 명령으로 동작을 확인합니다.

```bash
# 1. Gateway를 통한 정상 요청 (JWT + 시크릿 헤더 자동 주입) → 200 또는 인증 오류
curl -H "Authorization: Bearer <JWT>" http://localhost:8080/api/auctions

# 2. auction-service 직접 호출 (시크릿 헤더 없음) → 403 Forbidden
curl http://localhost:8081/auctions
```
