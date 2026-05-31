# Observability 운영 가이드

Prometheus, Grafana, Loki, Tempo, Alloy의 역할과 GKE 배포 후 검증 방법을 정리합니다.

## 구성

| 구성 요소 | 역할 |
|-----------|------|
| Prometheus | Kubernetes 및 Spring Boot 애플리케이션 메트릭 수집 |
| Grafana | 메트릭, 로그, 트레이스 탐색 |
| Loki | Alloy가 전달한 노드 및 Pod 로그 저장 |
| Tempo | Alloy가 전달한 OTLP 트레이스 저장 |
| Alloy | 모든 워크로드 노드에서 로그를 수집해 Loki로 전송하고, 애플리케이션 OTLP 트레이스를 Tempo로 전달 |

## 애플리케이션 메트릭

Spring Boot 앱은 `/actuator/prometheus`에서 Prometheus 형식의 메트릭을 노출합니다.
`auction-applications` ServiceMonitor는 `auction` 네임스페이스에서 `monitoring: prometheus`
라벨이 붙은 Service를 선택하고 `http` 포트를 30초마다 수집합니다.

수집 대상은 다음 6개입니다.

- `api-gateway`
- `auction-service`
- `bid-service`
- `user-service`
- `notification-service`
- `auction-streams`

`management.otlp.metrics.export.enabled: false`는 OTLP metrics push만 비활성화합니다.
Prometheus pull 방식의 `/actuator/prometheus` 수집에는 영향을 주지 않습니다.

## Spot 노드 로그

API 서비스는 비용 절감을 위해 `spot=true:NoSchedule` taint가 있는 Spot 노드에 배치됩니다.
Alloy는 DaemonSet이며 동일한 taint toleration을 갖습니다. 따라서 기본 노드와 Spot 노드
양쪽에서 실행되어 각 노드의 `/var/log`를 읽습니다.

애플리케이션 Pod는 다음 Kubernetes 권장 라벨을 사용합니다.

- `app.kubernetes.io/name`
- `app.kubernetes.io/component`
- `app.kubernetes.io/part-of`

기존 `app` 라벨은 Service selector 호환성을 위해 유지합니다. Alloy는
`app.kubernetes.io/name`을 Loki의 `app` 라벨로 변환합니다. Tempo의 `service.name`도
Grafana 데이터소스 설정에서 Loki의 `app` 라벨과 연결됩니다.

## 로컬 렌더링 검증

```bash
kubectl kustomize infra/k8s/overlays/dev

helm template alloy grafana/alloy \
  --version 1.6.2 \
  --namespace monitoring \
  -f infra/helm/alloy/values.yaml
```

Helm 렌더링 결과의 Alloy DaemonSet에 다음 toleration이 있어야 합니다.

```yaml
- key: spot
  operator: Equal
  value: "true"
  effect: NoSchedule
```

## 배포 후 검증

### Prometheus

```bash
kubectl get servicemonitors -A
```

```promql
up{namespace="auction", scrape_group="auction-applications"}
count(up{namespace="auction", scrape_group="auction-applications"})
http_server_requests_seconds_count
```

`count(...)` 결과의 기대값은 `6`입니다. Grafana의 `SLO 보완 지표` 대시보드에서도
`등록된 애플리케이션 scrape 대상 수` 패널을 확인합니다. 대상이 아예 등록되지 않으면
`up == 0`만으로는 감지할 수 없으므로 대상 수를 함께 확인해야 합니다.

### Alloy와 Loki

```bash
kubectl get pods -n monitoring -l app.kubernetes.io/name=alloy -o wide
kubectl get nodes -L cloud.google.com/gke-spot
```

기본 노드와 Spot 노드마다 Alloy Pod가 하나씩 실행되어야 합니다.

```logql
{namespace="auction", app="api-gateway"}
{namespace="auction", app="auction-service"}
```

### Tempo와 Loki 연결

애플리케이션은 JSON 콘솔 로그를 출력합니다. Actuator를 제외한 HTTP 요청이 끝나면
`traceId`, `spanId`, 요청 경로, 상태 코드, 처리 시간을 포함한 로그를 남깁니다.
Alloy는 JSON 로그의 `traceId`, `spanId`를 Loki structured metadata로 저장합니다.

트레이스는 다음 경로로 전달됩니다.

```text
Spring Boot 애플리케이션 → Alloy OTLP HTTP(4318) → Tempo OTLP gRPC(4317)
```

dev 환경은 Spring Boot OTLP export 주기를 기본값 `5s`에서 `1s`로 낮춰 로그가 Loki에
먼저 나타난 직후 Tempo 링크를 열었을 때 발생하는 조회 지연을 줄입니다.
Alloy exporter는 메모리 큐에서 최대 5분간 재시도합니다. Tempo 단기 장애는 흡수하지만,
Alloy Pod 재시작까지 보존되는 영속 큐는 아닙니다.

Alloy 서비스가 OTLP HTTP 포트를 노출하는지 확인합니다.

```bash
kubectl get service alloy -n monitoring
```

배포 시 Alloy가 먼저 동기화되어 `4318` 포트를 수신하는지 확인한 뒤 애플리케이션
ConfigMap을 반영합니다. 애플리케이션 endpoint를 먼저 바꾸면 동기화 사이에 트레이스가
일시적으로 유실될 수 있습니다.

Grafana Explore에서 Tempo 트레이스를 선택한 뒤 span의 `Logs for this span`을 실행합니다.
선택한 서비스의 Loki 로그가 `app` 라벨과 `traceId` 기준으로 조회되어야 합니다.

반대로 Loki에서 `HTTP 요청 처리 완료` 로그를 펼친 뒤 `View trace in Tempo` 링크를
선택하면 동일한 트레이스를 조회할 수 있습니다.
