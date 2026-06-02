# 성능 테스트 가이드

이 디렉토리는 성능 테스트의 **측정 절차와 결과 리포트**를 관리한다.
실행 자산(k6 스크립트·러너)은 레포 루트 `perf/`에 있다.

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
3. **워밍업**: ramp-up 구간이 JIT·커넥션 풀·RocksDB 예열을 겸한다.
   단, 배포 또는 Pod 재시작 직후에는 ramp-up만으로 충분하지 않을 수 있으므로 본 측정과 같은 설정으로
   워밍업 실행을 1회 수행한다. 현재 k6 요약은 ramp-up·유지(sustain)·ramp-down 전체를 집계하므로
   before/after에서 워밍업 여부와 단계 구성을 동일하게 유지한다.
4. **추적 가능성**: 매 실행은 클러스터에 배포된 bid-service 이미지의 git SHA로 태깅되어
   `perf/results/<git-sha>/`에 격리 저장된다. 결과 리포트에는 측정 대상 git SHA와 측정 일시를 기록하고,
   원시 결과는 수정하지 않는다.

## 산출물 관리

성능 테스트 결과는 다음과 같이 관리한다.

| 산출물 | 위치 | Git 관리 | 역할 |
|--------|------|-----------|------|
| 결과 리포트(.md) | `docs/performance/p3-*.md` | 커밋 | 환경·시나리오·before/after 표·분석 |
| 원시 결과(JSON·로그) | `perf/results/<git-sha>/` | 제외 | k6 실행 결과. 재검증이 필요하면 별도 보관 |
| Grafana 캡처 | `docs/performance/assets/` 또는 공유 링크 | 선택 | p99·CPU·lag 시계열 근거. 공개 시 민감 정보 제거 |

## 측정 도구

- **부하 생성**: [k6](https://grafana.com/docs/k6/latest/) — 클러스터 내부 Job으로 실행.
- **메트릭 수집**: GKE에 배포된 kube-prometheus-stack(Prometheus + Grafana).
- **관측 지표(P3)**: `POST /api/bids` p50/p95/p99, req/s, 서버 에러율 + (보조) bid-service CPU, 외부호출 레이턴시.

## 실행 방법 (P3 입찰 레이턴시)

### 사전 조건

- `kubectl`이 GKE 클러스터(`auction` 네임스페이스)를 가리키고 있을 것.
- 측정 대상 코드가 클러스터에 반영된 상태일 것.
- baseline과 after 측정 전에 배포된 git SHA를 확인할 것.
- 실행 자산의 변경 사항을 커밋해 러너 로그에 `-dirty` 경고가 없을 것.
- 클러스터에 다른 부하가 없을 것(공정성).

### 테스트 전후 상태 확인

baseline과 after는 각각 측정 직전에 같은 명령을 실행해 클러스터 상태를 기록한다.

```bash
./perf/check-cluster-state.sh \
  | tee "perf/results/cluster-state-$(date -u +%Y%m%dT%H%M%SZ).log"
```

다음 조건을 확인한 뒤 본 측정을 시작한다.

- 모든 Pod와 Deployment가 준비 상태이고, 실행 중인 Job이 없을 것.
- baseline과 after의 노드 수, 노드 풀, 애플리케이션 Pod 수가 같을 것.
- Gateway health가 `UP`이고 Debezium connector와 task가 `RUNNING`일 것.
- Kafka consumer lag가 짧은 간격으로 다시 확인했을 때 계속 증가하지 않을 것.
- `kubectl top`으로 확인한 CPU·메모리 사용량이 두 측정에서 비슷한 수준일 것.

### 절차

```bash
# 1. 테스트 전 클러스터 상태 기록
./perf/check-cluster-state.sh \
  | tee "perf/results/cluster-state-$(date -u +%Y%m%dT%H%M%SZ).log"

# 2. (권장) 먼저 스모크로 파이프라인 동작만 검증 — 초경량(VU 5/30s)
PERF_CONFIG=perf/config/smoke.env ./perf/run-perf.sh
#    → 가입·경매생성·CDC 반영 대기·입찰·결과파일 생성까지 정상인지 확인
#    → 결과는 results/<git-sha>-smoke/ 에 격리 저장(본 측정과 안 섞임)

# 3. 스모크가 만든 이벤트 처리가 안정화됐는지 다시 기록
./perf/check-cluster-state.sh \
  | tee "perf/results/cluster-state-$(date -u +%Y%m%dT%H%M%SZ).log"

# 4. 배포 또는 Pod 재시작 직후라면 워밍업 실행
#    → 본 측정과 같은 설정으로 1회 실행한다.
./perf/run-perf.sh
./perf/run-perf.sh stop
#    → CPU·lag가 안정화될 때까지 기다린 뒤 상태를 기록한다.
./perf/check-cluster-state.sh \
  | tee "perf/results/cluster-state-$(date -u +%Y%m%dT%H%M%SZ).log"

# 5. 본 측정 — baseline.env 고정값으로 실행
./perf/run-perf.sh

# 6. 테스트 후 클러스터 상태 기록
./perf/check-cluster-state.sh \
  | tee "perf/results/cluster-state-$(date -u +%Y%m%dT%H%M%SZ).log"

# 7. (필요 시) 실행 중인 Job 중단/정리
./perf/run-perf.sh stop
```

실행이 끝나면 `perf/results/<git-sha>/` 에 다음이 저장된다.

- `k6-<timestamp>.log` — k6 전체 출력(사람용 요약 표 포함)
- `summary-<timestamp>.json` — 기계 판독용 전체 메트릭

### 결과를 리포트에 반영

1. `summary-*.json` 또는 로그 말미의 요약 표에서 p50/p95/p99·req/s·에러율을 확인한다.
2. Grafana에서 측정 시간대의 bid-service 패널을 캡처한다.
3. `docs/performance/p3-bid-latency.md`의 표에 baseline 행(또는 after 행)을 채운다.
4. 원시 결과를 별도 보관했다면 리포트에 접근 가능한 링크를 기록한다.

## 실행 시 주의사항

- **대상 주소**: 부하는 클러스터 내부 k6 Job에서 `api-gateway-service`로 보낸다. 외부 LoadBalancer/Ingress를 사용하지 않는다.
- **부하 규모**: `baseline.env`의 `MAX_VUS`/`SUSTAIN_DUR`를 변경하면 before/after 비교 조건도 달라진다.
- **정리**: Job은 `ttlSecondsAfterFinished: 300`으로 자동 삭제된다. 측정 후 `./perf/run-perf.sh stop`으로
  ConfigMap까지 정리한다.
- **데이터**: 테스트는 경매/입찰 데이터를 생성한다. 반복 측정 시 DB가 누적되므로, 장기적으로는 측정 후
  테스트 데이터 정리 정책을 마련한다.
