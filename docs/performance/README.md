# 성능 테스트 가이드

이 디렉토리는 성능 개선 작업의 **방법론과 측정 결과**를 관리한다.
작업 지시서는 `temp/30_performance-improvement-plan.md`, 실행 자산(k6 스크립트·러너)은 레포 루트 `perf/`에 있다.

## 문서 구성

| 파일 | 내용 |
|------|------|
| `README.md` (이 파일) | 측정 절차·도구·산출물 규칙 |
| `p3-bid-latency.md` | P3(입찰 외부호출 병렬화) before/after 리포트 |

## 측정 원칙 (before/after 공정성)

성능 개선은 "수정 전/후를 **같은 방법·같은 조건**으로 측정해 수치로 입증"하는 것이 핵심이다.
다음을 고정한다.

1. **부하 파라미터 고정**: VU 수·단계 시간·think time·시나리오는 `perf/config/baseline.env` 한 파일에만 둔다.
   baseline을 한 번 측정한 뒤로는 이 파일을 수정하지 않는다. before/after가 물리적으로 같은 값을 참조한다.
2. **환경 고정**: 노드/파드 리소스, Kafka 파티션 수, 데이터 규모를 양쪽 측정에서 동일하게 유지한다.
3. **워밍업**: ramp-up 구간이 JIT·커넥션 풀·RocksDB 예열을 겸한다. 헤드라인 수치는 유지(sustain) 구간이 좌우한다.
4. **추적 가능성**: 매 실행은 git SHA로 태깅되어 `perf/results/<git-sha>/`에 격리 저장된다.
   "이 커밋 = 이 수치"가 추적된다. **수치를 손으로 옮겨 적지 않는다.**

## 실무 산출물 (구글 스프레드시트 대신)

ad-hoc 측정은 스프레드시트로도 충분하지만, 코드 프로젝트에서는 **레포에 커밋되는 3종 세트**가 정석이다.

| 산출물 | 위치 | 역할 |
|--------|------|------|
| 성능 리포트(.md) | `docs/performance/p3-*.md` | 사람이 읽는 결론: 환경·시나리오·before/after 표·분석 |
| 원시 결과(JSON) | `perf/results/<git-sha>/summary-*.json` | 재현·검증 가능한 raw data (k6 `handleSummary` 출력) |
| Grafana 스냅샷 | 리포트에 캡처/링크 첨부 | p99·CPU·lag 시계열 증거 |

## 측정 도구

- **부하 생성**: [k6](https://grafana.com/docs/k6/latest/) — 클러스터 내부 Job으로 실행(egress 과금 없음).
- **메트릭 수집**: GKE에 배포된 kube-prometheus-stack(Prometheus + Grafana).
- **관측 지표(P3)**: `POST /api/bids` p50/p95/p99, req/s, 서버 에러율 + (보조) bid-service CPU, 외부호출 레이턴시.

## 실행 방법 (P3 입찰 레이턴시)

### 사전 조건

- `kubectl`이 GKE 클러스터(`auction` 네임스페이스)를 가리키고 있을 것.
- 측정 대상 코드가 main에 머지되어 ArgoCD로 클러스터에 반영된 상태일 것
  (baseline은 개선 전 커밋, after는 개선 후 커밋 기준).
- 클러스터에 다른 부하가 없을 것(공정성).

### 절차

> 머지 불필요: 이 테스트는 **이미 클러스터에 떠 있는 앱 서비스**를 대상으로 하며,
> perf 스크립트는 로컬에서 ConfigMap+Job으로 올라간다. perf 코드 자체를 머지할 필요는 없다.

```bash
# 1. 현재 클러스터 컨텍스트 확인 (실수로 다른 클러스터에 쏘지 않도록)
kubectl config current-context

# 2. (권장) 먼저 스모크로 파이프라인 동작만 검증 — 초경량(VU 5/30s)
PERF_CONFIG=perf/config/smoke.env ./perf/run-perf.sh
#    → 가입·경매생성·CDC 반영 대기·입찰·결과파일 생성까지 정상인지 확인
#    → 결과는 results/<git-sha>-smoke/ 에 격리 저장(본 측정과 안 섞임)

# 3. 본 측정 — baseline.env 고정값으로 실행
./perf/run-perf.sh

# 4. (필요 시) 실행 중인 Job 중단/정리
./perf/run-perf.sh stop
```

실행이 끝나면 `perf/results/<git-sha>/` 에 다음이 저장된다.

- `k6-<timestamp>.log` — k6 전체 출력(사람용 요약 표 포함)
- `summary-<timestamp>.json` — 기계 판독용 전체 메트릭

### 결과를 리포트에 반영

1. `summary-*.json` 또는 로그 말미의 요약 표에서 p50/p95/p99·req/s·에러율을 확인한다.
2. Grafana에서 측정 시간대의 bid-service 패널을 캡처한다.
3. `docs/performance/p3-bid-latency.md`의 표에 baseline 행(또는 after 행)을 채운다.

## 💰 무료 크레딧 절약 주의

- **네트워크**: 부하는 클러스터 내부(k6 Job → `api-gateway-service`)에서만 발생하므로 인터넷 egress 과금이 없다.
  외부 LoadBalancer/Ingress로 쏘지 말 것(egress + LB 시간당 과금).
- **컴퓨팅**: 진짜 비용 동인은 노드 가동 시간이다. `baseline.env`의 `MAX_VUS`/`SUSTAIN_DUR`를 키우면
  HPA·Cluster Autoscaler가 노드를 늘려 과금이 급증할 수 있다. **기본값(VU 50 / 5분)을 크게 올리지 말 것.**
- **정리**: Job은 `ttlSecondsAfterFinished: 300`으로 자동 삭제된다. 측정 후 `./perf/run-perf.sh stop`으로
  ConfigMap까지 정리하면 깔끔하다.
- **데이터**: 테스트는 경매/입찰 데이터를 생성한다. 반복 측정 시 DB가 누적되므로, 장기적으로는 측정 후
  테스트 데이터 정리 정책을 고려한다(현재 범위 밖).
