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

main 브랜치에 반영(push/merge)되면 App-of-Apps가 `sealed-secrets` ArgoCD Application을 감지하고
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

# 2. ArgoCD automated sync가 자동으로 재설치합니다. 아래 명령으로 완료 확인:
kubectl get pods -n monitoring
```

`automated: selfHeal: true` 설정으로 helm uninstall 직후 ArgoCD가 자동으로 재설치합니다.
재설치 후 모든 리소스의 SSA 필드 매니저가 `argocd-controller`로 설정됩니다.

## Step 7 — 완료 확인

```bash
# SealedSecret이 Secret으로 복호화되었는지 확인
kubectl get secret grafana-admin-secret -n monitoring

# Grafana Pod가 정상 기동하는지 확인
kubectl get pods -n monitoring -l app.kubernetes.io/name=grafana
```

---

## Loki MinIO 자격증명 설정

Loki Helm chart 내장 MinIO의 기본 자격증명(`supersecretpassword`)을 SealedSecret으로 교체합니다.
SealedSecret 파일과 관련 설정(`loki/values.yaml`, `kustomization.yaml`)은 이미 커밋되어 있으므로,
클러스터 교체 등으로 재봉인이 필요한 경우 아래 절차로 SealedSecret 파일만 다시 생성하여 커밋합니다.

> 시크릿 키명은 `rootUser` / `rootPassword` (camelCase) 를 사용한다.
> Loki 차트 내장 MinIO 서브차트(`minio/minio`)가 이 키를 참조하며,
> Bitnami MinIO 차트의 `auth.existingSecret` / `root-user` 키명과 다르다.

```bash
echo -n "MinIO root password: " && read -s MINIO_ROOT_PASSWORD && echo
printf '%s' "$MINIO_ROOT_PASSWORD" > /tmp/minio-root-password.txt
unset MINIO_ROOT_PASSWORD

kubectl create secret generic loki-minio-secret \
  --namespace monitoring \
  --from-literal=rootUser='loki' \
  --from-file=rootPassword=/tmp/minio-root-password.txt \
  --dry-run=client -o yaml \
  | kubeseal --format yaml \
  --controller-name=sealed-secrets \
  --controller-namespace=kube-system \
  > infra/k8s/base/monitoring/loki-minio-sealed-secret.yaml

rm -f /tmp/minio-root-password.txt
```

### values.yaml 연동 주의사항

`minio.existingSecret`만 설정하면 MinIO 자체는 올바른 자격증명으로 기동하지만,
**Loki Helm 차트 템플릿은 `minio.rootUser` / `minio.rootPassword` Helm 값을 기준으로 Loki 설정 파일을 생성**하기 때문에
secret 값과 무관하게 차트 기본값(`root-user` / `supersecretpassword`)이 Loki ConfigMap에 하드코딩됩니다.

이를 방지하기 위해 `infra/helm/loki/values.yaml`에 아래 설정이 반드시 포함되어야 합니다.

```yaml
loki:
  structuredConfig:
    common:
      storage:
        s3:
          access_key_id: "${rootUser}"       # Loki가 기동 시 환경변수로 치환
          secret_access_key: "${rootPassword}"

singleBinary:
  extraArgs:
    - -config.expand-env=true               # ${VAR} 치환 활성화 (기본값 false)
  extraEnvFrom:
    - secretRef:
        name: loki-minio-secret             # rootUser / rootPassword 를 환경변수로 주입
```

`singleBinary.extraEnvFrom`으로 secret 값을 환경변수로 주입하고, `-config.expand-env=true` 플래그와
`structuredConfig`의 `${VAR}` 구문을 통해 Loki가 기동 시 실제 자격증명으로 치환합니다.

> `loki.extraEnvFrom`은 Distributed/SimpleScalable 모드에서만 동작하며, SingleBinary 템플릿은 `singleBinary.extraEnvFrom`만 참조합니다.

---

## Loki MinIO 버킷 생성

MinIO는 PVC에 데이터를 저장하지만 **버킷은 자동 생성되지 않습니다.**
클러스터 교체 또는 MinIO PVC 초기화 후에는 아래 절차로 버킷을 수동 생성해야 합니다.

```bash
MINIO_USER=$(kubectl get secret loki-minio-secret -n monitoring -o jsonpath='{.data.rootUser}' | base64 --decode)
MINIO_PASS=$(kubectl get secret loki-minio-secret -n monitoring -o jsonpath='{.data.rootPassword}' | base64 --decode)

kubectl exec loki-minio-0 -n monitoring -- sh -c "
  mc alias set local http://localhost:9000 ${MINIO_USER} ${MINIO_PASS} && \
  mc mb local/chunks && \
  mc mb local/ruler
"
```

버킷 생성 확인:

```bash
kubectl exec loki-minio-0 -n monitoring -- mc ls local
```

> `chunks` — 로그 데이터 저장소 (필수)
> `ruler` — 알럿 규칙 저장소 (필수)

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
