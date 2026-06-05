# Preview Deploy & Smoke Test

feature 브랜치 코드를 main 머지 없이 GKE dev 환경에서 바로 검증하는 워크플로 가이드.

---

## 배경과 목적

기본 CD 파이프라인은 main 브랜치에만 작동한다.

```text
기존 흐름: 코드 변경 → PR → main 머지 → CI → CD → kubectl set image → smoke test
```

feature 브랜치의 코드를 검증하려면 반드시 main에 머지해야 하는 구조라,
버그 수정 → 머지 → 실패 확인 → 재수정 → 재머지 사이클이 반복된다.

`preview-smoke.yml` 워크플로는 이 문제를 완화한다.
Actions 탭에서 브랜치를 선택해 수동 트리거하면, 변경된 서비스만 preview 이미지로 배포하고
smoke test까지 실행한 뒤 자동으로 dev overlay에 선언된 이미지로 복구한다.

---

## 동작 방식

### 전체 흐름

```text
workflow_dispatch (브랜치 선택)
    │
    ▼
┌─────────────────────────────────┐
│ 1. detect-changes               │
│    main 대비 변경된 서비스 감지   │
└─────────────┬───────────────────┘
              │ 변경 서비스 목록
              ▼
┌─────────────────────────────────┐
│ 2. build-push (변경 서비스만)    │
│    이미지 빌드 & Artifact Registry│
│    push (태그: preview-<sha>)   │
└─────────────┬───────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────┐
│ 3. deploy-and-test                                  │
│                                                     │
│  ① 변경 서비스 kubectl set image (preview-<sha>)     │
│  ② rollout 대기                                     │
│  ③ Gateway 헬스체크 대기                             │
│  ④ smoke test 실행                                  │
│  ⑤ 복구: 변경 서비스 kubectl set image (dev overlay 이미지) │
│     (성공/실패 무관하게 항상 실행)                     │
└─────────────────────────────────────────────────────┘
```

### 배포 방식

ArgoCD를 조작하지 않고 **`kubectl set image`로 직접 배포**한다.
git 파일을 수정하지 않으므로 overlay 커밋도 발생하지 않는다.

```text
평상시
  ArgoCD → 인프라 Pod 관리 (Kafka, PostgreSQL 등)
  kubectl set image → Java 서비스 Pod 관리

워크플로 실행 중
  변경 서비스만 preview-<sha> 이미지로 교체

워크플로 종료 후 (복구)
  변경 서비스를 origin/main의 dev overlay newTag 이미지로 복구
```

> 인프라(Kafka, PostgreSQL 등)는 ArgoCD가 관리하고, Java 서비스는 GitHub Actions가 `kubectl set image`로 배포하는 **하이브리드 구조**다. 상세: [gke-deployment.md](./gke-deployment.md), [architecture.md](./architecture.md)

---

## 사용 방법

1. GitHub 저장소 → **Actions** 탭
2. **Preview Deploy & Smoke Test** 워크플로 선택
3. **Run workflow** → 테스트할 브랜치 선택 → **Run workflow** 클릭

---

## 변경 서비스 감지 규칙

`main` 대비 변경된 파일 경로를 기준으로 자동 감지한다.

| 변경 경로 | 빌드 대상 |
|-----------|-----------|
| `services/auction-service/**` | `auction-service` |
| `services/bid-service/**` | `bid-service` |
| `services/api-gateway/**` | `api-gateway` |
| `services/user-service/**` | `user-service` |
| `services/notification-service/**` | `notification-service` |
| `streams/auction-streams/**` | `auction-streams` |
| `*.gradle.kts`, `settings.gradle.kts`, `gradle/`, `infra/avro/` | **전체 서비스** |

서비스 코드 변경이 없으면 build-push를 skip하고 **현재 배포된 이미지 그대로** smoke test를 실행한다.

---

## GitHub Secrets & Variables 등록

preview-smoke.yml이 사용하는 값은 main CD와 동일하다.

### 필요한 Secrets

| 이름 | 설명 |
|------|------|
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | Workload Identity Federation 프로바이더 URI |
| `GCP_SERVICE_ACCOUNT` | Artifact Registry 푸시 권한이 있는 서비스 계정 이메일 |
| `GCP_PROJECT_ID` | GCP 프로젝트 ID |

### 필요한 Variables

| 이름 | 예시 값 | 설명 |
|------|---------|------|
| `SMOKE_TEST_BASE_URL` | `http://34.x.x.x` | API Gateway LoadBalancer 외부 IP |

ArgoCD 관련 Secret(`ARGOCD_AUTH_TOKEN`, `ARGOCD_SERVER`)은 **불필요**하다.

---

## 제약 사항

| 항목 | 내용 |
|------|------|
| DB 공유 | dev 환경과 동일한 PostgreSQL을 사용한다. smoke test로 생성된 데이터(테스트 유저, 경매, 입찰)가 dev DB에 남는다. |
| 동시 실행 | `concurrency`로 preview-smoke 동시 실행을 막는다. 단, 다른 CD/수동 배포와 동시에 실행하면 여전히 충돌할 수 있다. |
| 워크플로 강제 종료 | Actions에서 워크플로를 강제 취소하면 복구 step이 실행되지 않아 preview 이미지가 그대로 남을 수 있다. 이 경우 수동으로 복구해야 한다. |

### 수동 복구 방법

워크플로 강제 취소 등으로 preview 이미지가 클러스터에 남은 경우, dev overlay의 고정 태그를 기준으로 복구한다.
아래 예시는 각 서비스 overlay의 `newTag` 값을 확인한 뒤 적용한다.

```bash
REGISTRY="asia-northeast3-docker.pkg.dev/<GCP_PROJECT_ID>/auction-images"

for svc in api-gateway auction-service bid-service user-service notification-service auction-streams; do
  tag=$(awk '
    $1 == "newTag:" { print $2; exit }
  ' "infra/k8s/overlays/dev/${svc}/kustomization.yaml")

  kubectl set image deployment/${svc}-deployment \
    ${svc}=${REGISTRY}/${svc}:${tag} \
    -n auction
done
```
