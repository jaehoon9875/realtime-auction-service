# E2E Smoke Test

배포된 GKE 환경에서 핵심 기능이 정상 동작하는지 확인하는 E2E Smoke Test 가이드.

---

## 목적과 범위

Smoke Test는 "배포 후 서비스가 살아있는가"를 확인한다.
모든 케이스를 커버하는 게 아니라, **서비스 간 연결이 끊기지 않았는지** 빠르게 검증한다.

| 구분 | 도구 | 실행 시점 |
|---|---|---|
| 서비스별 통합 테스트 (Testcontainers) | JUnit + Testcontainers | CI (PR·main push) |
| E2E Smoke Test | 셸 스크립트 + curl | CD (main push 후 자동) |

---

## 시나리오

### S1. Health Check
- `GET /actuator/health` → Gateway 응답 및 `status: UP` 확인

### S2. Happy Path
전체 서비스 간 연결을 검증하는 메인 시나리오.

```text
User A 회원가입 + 로그인 → JWT
User B 회원가입 + 로그인 → JWT
User A → 경매 생성 → auctionId 반환 확인
  └─ Debezium CDC → auction-events → Kafka Streams State Store (5초 대기)
User B → 입찰 (재시도 최대 3회)
  └─ bid-service → auction-streams State Store 검증 → bid-events 발행
User B → 경매 조회 → currentPrice = 입찰가 확인 (최대 6회 × 3초 폴링, CDC→Streams 지연 대응)
```

---

## 파일 구조

```text
e2e/
├── smoke.sh              # 진입점
├── lib/
│   ├── assert.sh         # assert_status, assert_contains, assert_not_empty
│   └── http.sh           # curl 래퍼 (HTTP_STATUS, HTTP_BODY 변수)
└── scenarios/
    ├── 01_health.sh      # S1: Health Check
    └── 02_happy_path.sh  # S2: 전체 흐름
```

---

## 로컬 실행

```bash
BASE_URL=http://<gateway-loadbalancer-ip> ./e2e/smoke.sh
```

실행 결과 예시:
```text
========================================
 Smoke Test  →  http://34.x.x.x
========================================

[ 01. Health Check ]
  [PASS] Gateway /actuator/health 응답
  [PASS] Gateway status UP

[ 02. Happy Path ]
  [PASS] User A 회원가입
  [PASS] User B 회원가입
  [PASS] User A 로그인
  [PASS] User A accessToken 발급
  ...
  currentPrice 대기 1/6 (HTTP 200)...
  [PASS] 경매 조회
  [PASS] currentPrice=15000 반영

========================================
 결과: PASS 13  FAIL 0
========================================
```

---

## CD 파이프라인 연동

`main` 브랜치 push → CI 통과 → CD 실행 순서:

```text
1. update-and-deploy  이미지 태그를 infra/k8s/overlays/dev에 커밋·푸시 + kubectl set image로 직접 배포
2. smoke-test         Gateway 헬스체크 대기 (최대 5분 폴링) → smoke.sh 실행
```

### 대기 로직

`smoke-test` 잡은 Gateway `/actuator/health`를 10초 간격으로 폴링한다.
UP 응답이 오면 즉시 smoke test를 실행하고, 5분(30회) 안에 응답이 없으면 실패 처리한다.

> **한계**: 폴링은 Gateway가 살아있는지만 확인하므로, 이전 버전 Pod가 아직 요청을 처리 중인 경우
> 새 이미지 배포 전에 smoke test가 실행될 수 있다. 대부분의 상황에서 허용 가능한 트레이드오프.

---

## 수동 실행

`SMOKE_TEST_BASE_URL` Variable 미설정, 배포 없이 재검증 등 필요 시 수동으로 실행할 수 있다.

**GitHub Actions UI에서 실행:**

1. GitHub 저장소 → **Actions** → **Smoke Test** 워크플로 선택
2. **Run workflow** 버튼 클릭 → **Run workflow** 확인

`SMOKE_TEST_BASE_URL` Repository Variable이 등록되어 있어야 한다 (등록 방법은 위 [GitHub Secrets & Variables 등록](#github-secrets--variables-등록) 섹션 참고).

---

## GitHub Secrets & Variables 등록

### Secrets vs Variables 차이

| 구분 | 용도 | 노출 여부 |
|---|---|---|
| **Secrets** | 비밀번호, API 키, 인증서 등 민감 정보 | 로그에 마스킹 처리됨 |
| **Variables** | URL, 프로젝트 ID 등 민감하지 않은 설정값 | 로그에 그대로 노출 |

등록 경로: **GitHub 저장소 → Settings → Secrets and variables → Actions**

---

### 전체 목록

#### Repository Secrets

| 이름 | 설명 | 어느 워크플로에서 사용 |
|---|---|---|
| `GCP_PROJECT_ID` | GCP 프로젝트 ID | CI (docker-build-push), CD (update-and-deploy) |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | Workload Identity Federation 프로바이더 URI | CI (docker-build-push) |
| `GCP_SERVICE_ACCOUNT` | Artifact Registry 푸시 권한이 있는 서비스 계정 이메일 | CI (docker-build-push) |
| `APP_ID` | GitHub App ID (브랜치 보호 우회용) | CD (update-and-deploy) |
| `APP_PRIVATE_KEY` | GitHub App 프라이빗 키 (PEM 형식) | CD (update-and-deploy) |

#### Repository Variables

| 이름 | 예시 값 | 설명 | 어느 워크플로에서 사용 |
|---|---|---|---|
| `SMOKE_TEST_BASE_URL` | `http://34.x.x.x` | API Gateway LoadBalancer 외부 IP | CD (smoke-test) |

---

### 등록 방법

#### Repository Secrets 등록

1. 저장소 **Settings** → **Secrets and variables** → **Actions** 클릭
2. **Secrets** 탭 → **New repository secret**
3. Name과 Secret 값 입력 후 **Add secret**

#### Repository Variables 등록

1. 저장소 **Settings** → **Secrets and variables** → **Actions** 클릭
2. **Variables** 탭 → **New repository variable**
3. Name과 Value 입력 후 **Add variable**

---

### GCP 관련 Secrets 발급 방법

#### GCP_PROJECT_ID

```bash
gcloud config get-value project
```

#### GCP_WORKLOAD_IDENTITY_PROVIDER, GCP_SERVICE_ACCOUNT

Workload Identity Federation을 사용하면 GCP 서비스 계정 키 파일 없이 GitHub Actions에서 GCP 인증이 가능하다.

설정 방법은 `infra/terraform/` 또는 cloud-sre-platform 저장소 README 참고.
발급된 값 형식:

```text
GCP_WORKLOAD_IDENTITY_PROVIDER: projects/123456789/locations/global/workloadIdentityPools/my-pool/providers/my-provider
GCP_SERVICE_ACCOUNT: github-actions@my-project.iam.gserviceaccount.com
```

#### APP_ID, APP_PRIVATE_KEY

브랜치 보호 정책이 적용된 `main`에 bot이 직접 push하기 위해 GitHub App 토큰을 사용한다.
일반 `GITHUB_TOKEN`은 보호된 브랜치에 push 권한이 없다.

1. GitHub → **Settings** → **Developer settings** → **GitHub Apps** → **New GitHub App**
2. App 이름 입력, Webhook 비활성화
3. Permissions: **Contents** → Read and write
4. 생성 후 **App ID** 확인 → `APP_ID`에 등록
5. **Generate a private key** → 다운로드된 `.pem` 파일 내용 전체 → `APP_PRIVATE_KEY`에 등록
6. 저장소에 App 설치: App 페이지 → **Install App** → 해당 저장소 선택

#### SMOKE_TEST_BASE_URL

GKE에 배포된 API Gateway의 외부 IP:

```bash
kubectl get svc api-gateway-service -n auction
# EXTERNAL-IP 컬럼 값을 사용
```

---

## 시나리오 추가 방법

1. `e2e/scenarios/` 아래에 `03_xxx.sh` 파일 생성
2. `http_get` / `http_post` 함수로 요청, `assert_*` 함수로 검증
3. `e2e/smoke.sh`에 `run_scenario` 한 줄 추가

```bash
# e2e/smoke.sh
run_scenario "03. 새 시나리오" "${SCRIPT_DIR}/scenarios/03_xxx.sh"
```
