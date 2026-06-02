# P3 — 입찰 외부 호출 병렬화 (before/after)

> 측정 절차: [README.md](./README.md)

## 1. 개선 대상

`BidService.placeBid`(`services/bid-service/.../service/BidService.java`)는 입찰 핫패스에서
두 외부 호출을 **순차** 실행한다.

1. `auctionServiceClient.getAuction()` → auction-service REST
2. `auctionStreamsClient.getCurrentPrice()` → auction-streams IQ REST

두 조회는 `auctionId`만 입력으로 받아 서로 독립적인데 직렬로 실행되어, 입찰 API 레이턴시에
2 RTT가 그대로 가산된다.

**구현**: Spring이 수명주기를 관리하는 공용 Virtual Thread executor에서 두 조회를 `CompletableFuture`로
병렬 실행한다. 요청 스레드의 트레이싱/MDC 컨텍스트는 `ContextSnapshotFactory`로 스냅샷을 떠 조회
스레드에 전파해, 분산 추적 span이 끊기지 않게 한다. 결과 검증 순서는 기존과 동일하게 존재 → 상태 →
금액 순으로 유지하고, 앞 단계에서 입찰이 거절되면 최고가 조회 Future의 결과를 버린다.

**기대 효과**: `POST /api/bids` p95/p99 레이턴시 약 -1 RTT.

**트레이드오프**: 경매 미존재 또는 종료 상태에서도 최고가 조회가 이미 시작될 수 있다. 이때 Future를
`cancel`해도 **이미 발사된 HTTP 호출 자체는 인터럽트되지 않고 완료**된다(`CompletableFuture.cancel`의
한계). 즉 드문 케이스에서 1회의 불필요한 조회가 백그라운드에서 끝나지만, 정상 입찰 핫패스의 레이턴시
개선을 우선한다.

## 2. 측정 환경

| 항목 | 값 |
|------|-----|
| 클러스터 | GKE (`auction` 네임스페이스) |
| 부하 도구 | k6 (in-cluster Job, `perf/k6/bid-latency.js`) |
| 부하 설정 | `perf/config/baseline.env` (VU 50 / ramp-up 30s / 유지 5m / sleep 0.3s) |
| 시나리오 | spread (경매 20개에 입찰 분산) |
| 메트릭 | k6 요약 + Grafana(bid-service) |
| 노드 | `e2-standard-4` 2대 + Spot `e2-standard-2` 1대 |
| 주요 파드 리소스 | gateway·auction·bid: request `100m` / limit `500m`, streams: request `200m` / limit `1 CPU` |
| Kafka 파티션 수 | `auction-events`, `bid-events`, `notification-events`: 각 3 |

> before/after는 위 설정을 **동일하게** 유지한 상태에서만 비교 가능하다.

## 3. 측정 결과

| 지표 | Baseline (개선 전) | After (개선 후) | 변화 |
|------|-------------------|----------------|------|
| git SHA | `39c5ff9` | `37ef9eb` | — |
| 측정 일시(UTC) | `2026-06-02T04:54:06Z` | `2026-06-02T07:17:42Z` | — |
| 총 요청 수 | 33,459 | 42,332 | +26.52% |
| 입찰 iteration 수 | 33,397 | 42,270 | +26.57% |
| 처리량 (req/s) | 95.29 | 120.85 | +26.82% |
| 레이턴시 avg (ms) | 181.4 | 80.0 | -55.88% |
| 레이턴시 p50 (ms) | 134.3 | 64.0 | -52.35% |
| 레이턴시 p95 (ms) | 490.2 | 198.8 | -59.45% |
| 레이턴시 p99 (ms) | 706.6 | 299.9 | -57.55% |
| 레이턴시 max (ms) | 1,739.5 | 658.9 | -62.12% |
| 서버 에러율 | 0.00% | 0.00% | 유지 |
| 비즈니스 400 비율 (참고) | 62.07% | 60.60% | -1.48%p |

> 원시 데이터(로컬, Git 제외): `perf/results/<git-sha>/summary-*.json`
> 외부 보관 링크: _(필요 시 기입)_

### Grafana 캡처

- Baseline: _(캡처 예정)_
- After: _(민감 정보를 제거한 이미지 또는 공유 링크)_

## 4. 분석

Baseline 측정은 서버 오류 없이 완료됐다. 테스트 전후 Gateway health는 `UP`, Debezium connector와 task는
모두 `RUNNING` 상태였고, `auction-streams` consumer lag는 부하 중에도 전 파티션 `0`을 유지했다.

유지 구간 중 Spot 노드 CPU는 `100~101%`까지 올라갔다. gateway·auction-service·bid-service가 같은 Spot
노드에 배치되어 있어, after 측정에서도 동일한 노드 구성과 파드 배치를 유지해야 P3 개선 효과를 공정하게
비교할 수 있다.

After 측정은 baseline과 같은 노드 구성, 파드 배치, 부하 설정으로 수행했다. 유지 구간 중 Spot 노드 CPU는
`102%`까지 올라가 baseline과 마찬가지로 CPU 한계에 도달했다. 이 조건에서 처리량은 `26.82%` 증가했고,
p95는 `59.45%`, p99는 `57.55%` 감소했다. 서버 오류도 발생하지 않았다. 독립적인 외부 조회를 병렬화한
효과가 입찰 핫패스의 지연시간 감소와 처리량 증가로 이어졌다고 판단한다.

`notification-service` consumer lag는 baseline 유지 구간 중 파티션별 최대 `4~6`까지 일시적으로 증가했지만,
테스트 종료 직후 기존 수준인 파티션별 `1`로 회복했다. After 공식 측정에서 확인한 유지 구간 샘플과
종료 직후 샘플은 모두 파티션별 `1`이었다. P3 비교의 핵심 지표는 아니지만, 별도 병목 후보로 추적할
가치가 있다.

### After 워밍업 관찰

After 배포 직후 스모크와 상태 확인을 마치고 실행한 첫 본 측정은 워밍업 참고값으로 분리했다. 새 JVM과
커넥션 풀이 충분히 예열되지 않은 상태에서 테스트 구간 동안 처리량이 계속 증가했고, `81.21 req/s`,
p95 `672.1 ms`, p99 `1,004.3 ms`, 서버 오류 `0.007%`를 기록했다. 같은 Pod에서 60초 이상 안정화한 뒤
동일한 설정으로 재실행한 값을 공식 After 결과로 사용했다.

CD 직후 E2E 스모크에서도 입찰 API 호출은 성공했지만 State Store의 최고가 반영이 제한 시간 내 확인되지
않는 일시적인 실패가 있었다. State Store 복구 또는 CDC 전파 타이밍의 영향으로 추정되며, 추가로 기다린
뒤 다시 실행하면 통과했다. 배포 직후 성능 측정에는 Pod 준비 상태뿐 아니라 파이프라인 안정화와 워밍업
확인이 필요하다.

## 5. 비고 / 측정 시 주의

- **비즈니스 400은 정상**: 경합 시나리오에서 입찰가가 현재가 이하면 400이 반환되지만,
  이 400 응답도 외부 호출 2회를 모두 거친 뒤 반환되므로 측정 대상 핫패스를 그대로 경유한다.
  따라서 레이턴시 통계에 포함하고, 에러율(`bid_error_rate`)에는 5xx/타임아웃만 집계한다.
- **CDC 전파 지연**: setup 단계에서 경매 생성 후 State Store 반영까지 폴링 대기한다(최대 ~60s).
