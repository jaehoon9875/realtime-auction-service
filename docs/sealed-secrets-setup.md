# Sealed Secrets 초기 설정 절차

Sealed Secrets Controller와 Grafana admin 비밀번호 SealedSecret 초기 설정 절차입니다.
이 문서의 작업은 Git 커밋으로 관리할 수 없는 1회성 수동 명령어만 포함합니다.

## 전제 조건

- GKE 클러스터에 접근 가능한 kubeconfig 설정 완료
- ArgoCD가 실행 중이고 main 브랜치가 sync 대상

## Step 1 — kubeseal CLI 설치 (최초 1회)

macOS (Homebrew):

```bash
brew install kubeseal
```

Linux·Windows는 [bitnami-labs/sealed-secrets — Kubeseal](https://github.com/bitnami-labs/sealed-secrets#kubeseal)의 공식 설치 절차를 따릅니다 (바이너리 다운로드, `go install`, Windows는 [Releases](https://github.com/bitnami-labs/sealed-secrets/releases)의 `windows-amd64` 아카이브).

## Step 2 — 브랜치 push 및 Sealed Secrets Controller 설치 확인

브랜치를 push하면 App-of-Apps가 `sealed-secrets` ArgoCD Application을 감지하고
Helm으로 Controller를 `kube-system`에 자동 설치합니다.

Controller Pod가 Running 상태인지 확인합니다.

```bash
kubectl get pods -n kube-system -l app.kubernetes.io/name=sealed-secrets
```

출력 예시:

```text
NAME                               READY   STATUS    RESTARTS   AGE
sealed-secrets-XXXXXXXXX-XXXXX    1/1     Running   0          1m
```

## Step 3 — SealedSecret 생성

Controller가 Running 상태가 된 후 실행합니다.

> `--from-literal=admin-password='...'`처럼 명령줄에 비밀번호를 넣으면 셸 히스토리·프로세스 목록에 남을 수 있습니다.
> 아래처럼 `read -s`로 입력하거나, 비밀번호만 담은 파일을 `--from-file`로 넘기세요.

```bash
echo -n "Grafana admin password: " && read -s GRAFANA_ADMIN_PASSWORD && echo
printf '%s' "$GRAFANA_ADMIN_PASSWORD" > /tmp/grafana-admin-password.txt
unset GRAFANA_ADMIN_PASSWORD

kubectl create secret generic grafana-admin-secret \
  --namespace monitoring \
  --from-literal=admin-user='admin' \
  --from-file=admin-password=/tmp/grafana-admin-password.txt \
  --dry-run=client -o yaml \
  | kubeseal --format yaml \
  --controller-name=sealed-secrets \
  --controller-namespace=kube-system \
  > infra/k8s/base/monitoring/grafana-admin-sealed-secret.yaml

rm -f /tmp/grafana-admin-password.txt
```

생성된 파일은 암호화되어 있으므로 Git에 커밋해도 안전합니다.

## Step 4 — kustomization.yaml에 파일 추가

`infra/k8s/base/monitoring/kustomization.yaml`의 `resources` 항목을 수정합니다.

```yaml
resources:
  - grafana-admin-sealed-secret.yaml
```

## Step 5 — 커밋 및 push

```bash
git add infra/k8s/base/monitoring/
git commit -m "infra: Grafana admin 비밀번호 SealedSecret 추가"
git push
```

ArgoCD `monitoring-secrets` Application이 SealedSecret을 클러스터에 적용합니다.

## Step 6 — kube-prometheus-stack 클린 마이그레이션

기존 `helm install` CLI로 설치된 릴리즈는 `helm` SSA 필드 매니저가 리소스 소유권을 갖습니다.
ArgoCD가 SSA로 일관되게 관리하려면 기존 릴리즈를 제거하고 ArgoCD가 처음부터 설치해야 합니다.

> 이 과정에서 Prometheus/Grafana가 약 1-2분간 중단됩니다.

```bash
# 1. 기존 Helm 릴리즈 제거 (PVC 등 리소스는 보존)
helm uninstall kube-prometheus-stack -n monitoring

# 2. ArgoCD가 kube-prometheus-stack을 재설치할 때까지 대기
#    ArgoCD UI → kube-prometheus-stack → Sync 또는:
argocd app sync kube-prometheus-stack
```

재설치 후 모든 리소스의 SSA 필드 매니저가 `argocd-controller`로 설정됩니다.

## Step 7 — 완료 확인

```bash
# SealedSecret이 Secret으로 복호화되었는지 확인
kubectl get secret grafana-admin-secret -n monitoring

# Grafana Pod가 정상 기동하는지 확인
kubectl get pods -n monitoring -l app.kubernetes.io/name=grafana
```

---

## 클러스터 교체 시 재봉인

Sealed Secrets는 클러스터의 공개키로 암호화됩니다.
클러스터를 새로 생성하면 기존 SealedSecret을 복호화할 수 없으므로 Step 3–5를 다시 수행해야 합니다.

현재 클러스터의 공개키 확인:

```bash
kubeseal --fetch-cert \
  --controller-name=sealed-secrets \
  --controller-namespace=kube-system
```
