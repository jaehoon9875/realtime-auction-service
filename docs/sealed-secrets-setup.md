# Sealed Secrets 초기 설정 절차

Sealed Secrets Controller와 Grafana admin 비밀번호 SealedSecret 초기 설정 절차입니다.
이 문서의 작업은 Git 커밋으로 관리할 수 없는 1회성 수동 명령어만 포함합니다.

## 전제 조건

- GKE 클러스터에 접근 가능한 kubeconfig 설정 완료
- ArgoCD가 실행 중이고 `infra/kube-prometheus-stack-gitops` 브랜치(또는 main 병합 이후)가 sync 대상

## Step 1 — kubeseal CLI 설치 (최초 1회)

```bash
brew install kubeseal
```

## Step 2 — 브랜치 push 및 Sealed Secrets Controller 설치 확인

브랜치를 push하면 App-of-Apps가 `sealed-secrets` ArgoCD Application을 감지하고
Helm으로 Controller를 `kube-system`에 자동 설치합니다.

Controller Pod가 Running 상태인지 확인합니다.

```bash
kubectl get pods -n kube-system -l app.kubernetes.io/name=sealed-secrets
```

출력 예시:
```
NAME                               READY   STATUS    RESTARTS   AGE
sealed-secrets-XXXXXXXXX-XXXXX    1/1     Running   0          1m
```

## Step 3 — SealedSecret 생성

Controller가 Running 상태가 된 후 실행합니다.
`<원하는_비밀번호>` 부분을 실제 비밀번호로 교체합니다.

```bash
kubectl create secret generic grafana-admin-secret \
  --namespace monitoring \
  --from-literal=admin-user='admin' \
  --from-literal=admin-password='<원하는_비밀번호>' \
  --dry-run=client -o yaml \
  | kubeseal --format yaml \
  > infra/k8s/base/monitoring/grafana-admin-sealed-secret.yaml
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

## Step 6 — kube-prometheus-stack 최초 sync

`kube-prometheus-stack` ArgoCD Application이 기존 Helm 릴리즈를 인계받습니다.
최초 sync 시 ArgoCD UI에서 OutOfSync 상태로 표시되면 수동으로 Sync를 실행합니다.

```bash
argocd app sync kube-prometheus-stack
```

또는 ArgoCD UI → kube-prometheus-stack → Sync 클릭.

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
kubeseal --fetch-cert --controller-namespace kube-system
```
