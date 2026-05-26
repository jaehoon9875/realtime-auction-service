# Preview Deploy & Smoke Test

feature 브랜치 코드를 main 머지 없이 GKE dev 환경에서 바로 검증하는 워크플로 가이드.

---

## 배경과 목적

기본 CD 파이프라인은 main 브랜치에만 작동한다.

```
기존 흐름: 코드 변경 → PR → main 머지 → CI → CD → ArgoCD sync → smoke test
```

feature 브랜치의 코드를 검증하려면 반드시 main에 머지해야 하는 구조라,
버그 수정 → 머지 → 실패 확인 → 재수정 → 재머지 사이클이 반복된다.

`preview-smoke.yml` 워크플로는 이 문제를 해결한다.
Actions에서 브랜치를 선택해 수동 트리거하면, ArgoCD를 해당 브랜치 기준으로 임시 전환하고
smoke test까지 실행한 뒤 자동으로 main 상태로 복구한다.

---

## 동작 방식

### 전체 흐름

```
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
│  ① bootstrap selfHeal 끔 (자식 앱 변경 보호)          │
│  ② 서비스 앱 targetRevision → feature 브랜치          │
│  ③ 변경 서비스 kustomize 이미지 오버라이드             │
│  ④ 변경 서비스만 sync & 롤아웃 대기                   │
│  ⑤ Gateway 헬스체크 대기                             │
│  ⑥ smoke test 실행                                  │
│  ⑦ ArgoCD 복구 (main, 성공/실패 무관하게 항상 실행)   │
└─────────────────────────────────────────────────────┘
```

### ArgoCD 임시 전환 구조

ArgoCD는 Application 오브젝트의 `targetRevision` 필드를 기준으로 어떤 브랜치의 매니페스트를 배포할지 결정한다.
이 워크플로는 git 파일을 수정하지 않고, **클러스터 내 오브젝트만 직접 변경**한다.

```
평상시
  git(main) ←selfHeal→ 클러스터(main)

워크플로 실행 중
  git(main) 그대로
  클러스터 → feature 브랜치로 임시 전환
  selfHeal 비활성화 (git이 되돌리지 못하도록)

워크플로 종료 후
  클러스터 → main으로 복구
  selfHeal 재활성화
```

### selfHeal 끄는 순서가 중요한 이유

ArgoCD는 계층 구조로 동작한다.

```
auction-bootstrap (App of Apps)
  selfHeal: true → main의 infra/argocd/apps/*.yaml 감시
       ↓
  api-gateway, user-service, ... (자식 앱)
  selfHeal: true → main의 k8s 매니페스트 감시
```

자식 앱만 먼저 변경하면 bootstrap이 "git과 다르다"고 판단해 즉시 되돌린다.
따라서 **bootstrap을 먼저 끄고** 자식 앱을 변경해야 한다.

### 이미지 오버라이드 방식

변경된 서비스는 `preview-<7자리 SHA>` 태그로 새 이미지를 빌드·push한다.
ArgoCD CLI의 `--kustomize-image` 옵션으로 git 파일 수정 없이 인메모리 오버라이드를 설정한다.

```bash
argocd app set api-gateway \
  --kustomize-image "api-gateway=<registry>/api-gateway:preview-abc1234"
```

변경되지 않은 서비스는 빌드하지 않고, 이미 dev 환경에서 실행 중인 이미지를 그대로 사용한다.

### 복구 메커니즘

smoke test 결과(성공/실패)와 무관하게 `if: always()` 조건으로 복구 step이 항상 실행된다.

```bash
# kustomize 이미지 오버라이드 해제 + main 브랜치로 복구
argocd app unset <app> --kustomize-images
argocd app set <app> --revision main --self-heal=true

# bootstrap 복구 및 sync → 자식 앱 전체를 git 상태로 정리
argocd app set auction-bootstrap --self-heal=true --sync-policy automated
argocd app sync auction-bootstrap
```

bootstrap sync 이후 자식 앱들은 selfHeal이 다시 활성화되어 main 이미지로 자동 롤백된다.

---

## 사전 요구 사항

### GitHub Secrets

| 이름 | 설명 | 발급 방법 |
|---|---|---|
| `ARGOCD_AUTH_TOKEN` | ArgoCD API 인증 토큰 | 아래 [ArgoCD 토큰 발급](#argocd-토큰-발급) 참고 |

기존 CI/CD에서 사용 중인 `GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_SERVICE_ACCOUNT`, `GCP_PROJECT_ID`는 그대로 재사용된다.

### GitHub Variables

| 이름 | 예시 값 | 설명 | 발급 방법 |
|---|---|---|---|
| `ARGOCD_SERVER` | `34.xx.xx.xx` | ArgoCD LoadBalancer IP 또는 도메인 | 아래 [ArgoCD 서버 IP 확인](#argocd-서버-ip-확인) 참고 |

기존 `SMOKE_TEST_BASE_URL`은 그대로 재사용된다.

---

## 설정 방법

### ArgoCD 토큰 발급

**ArgoCD UI에서 발급:**

1. ArgoCD UI 접속 (`http://<ARGOCD_SERVER>`)
2. 좌측 하단 사용자 아이콘 → **User Info**
3. **Generate New Token** → 복사

**ArgoCD CLI에서 발급:**

```bash
argocd login <ARGOCD_SERVER> --insecure
argocd account generate-token --account admin
```

### ArgoCD 서버 IP 확인

```bash
kubectl get svc argocd-server -n argocd -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
```

### GitHub에 등록

등록 경로: 저장소 → **Settings** → **Secrets and variables** → **Actions**

**Secret 등록:**
1. **Secrets** 탭 → **New repository secret**
2. Name: `ARGOCD_AUTH_TOKEN`, Value: 위에서 발급한 토큰

**Variable 등록:**
1. **Variables** 탭 → **New repository variable**
2. Name: `ARGOCD_SERVER`, Value: ArgoCD IP (예: `34.xx.xx.xx`)

---

## 사용 방법

1. GitHub 저장소 → **Actions** 탭
2. 좌측 목록에서 **Preview Deploy & Smoke Test** 선택
3. **Run workflow** 버튼 클릭
4. 드롭다운에서 **테스트할 브랜치** 선택 → **Run workflow** 확인

### 실행 결과 예시

서비스 코드 변경이 있는 경우:

```text
detect-changes  → user-service, auction-service 변경 감지
build-push      → user-service:preview-abc1234, auction-service:preview-abc1234 빌드·push
deploy-and-test → ArgoCD 전환 → 이미지 오버라이드 → sync → smoke test → 복구
```

smoke test 스크립트만 변경된 경우 (서비스 코드 무변경):

```text
detect-changes  → 서비스 변경 없음
build-push      → 스킵
deploy-and-test → ArgoCD 전환 (기존 이미지 유지) → smoke test → 복구
```

---

## 제약 사항

| 항목 | 내용 |
|---|---|
| DB 공유 | dev 환경과 동일한 PostgreSQL을 사용한다. smoke test로 생성된 데이터(테스트 유저, 경매, 입찰)가 dev DB에 남는다. |
| 동시 실행 불가 | 두 브랜치를 동시에 테스트하면 ArgoCD 상태가 충돌한다. 워크플로를 순차적으로 실행해야 한다. |
| 워크플로 강제 종료 | Actions에서 워크플로를 강제 취소하면 복구 step이 실행되지 않아 ArgoCD가 feature 브랜치를 가리킨 채로 남을 수 있다. 이 경우 수동으로 복구해야 한다. |

### 수동 복구 방법

워크플로 강제 취소 등으로 ArgoCD가 비정상 상태가 된 경우:

```bash
argocd login <ARGOCD_SERVER> --insecure

SERVICE_APPS="api-gateway auction-service bid-service user-service notification-service auction-streams"
for app in $SERVICE_APPS; do
  argocd app unset "$app" --kustomize-images
  argocd app set "$app" --revision main --self-heal=true
done

argocd app set auction-bootstrap --self-heal=true --sync-policy automated
argocd app sync auction-bootstrap
```
