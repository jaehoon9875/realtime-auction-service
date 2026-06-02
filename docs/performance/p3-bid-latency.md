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
병렬 실행한다. 결과 검증 순서는 기존과 동일하게 존재 → 상태 → 금액 순으로 유지하고, 앞 단계에서 검증이
끝나면 아직 진행 중인 최고가 조회 Future를 취소한다.

**기대 효과**: `POST /api/bids` p95/p99 레이턴시 약 -1 RTT.

**트레이드오프**: 경매 미존재 또는 종료 상태에서도 최고가 조회가 먼저 시작될 수 있다. 정상 입찰 핫패스의
레이턴시 개선을 우선하고, 불필요한 조회는 Future 취소로 줄인다.

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
| git SHA | `39c5ff9` | _(채우기)_ | — |
| 측정 일시(UTC) | `2026-06-02T04:54:06Z` | | |
| 총 요청 수 | 33,459 | | |
| 입찰 iteration 수 | 33,397 | | |
| 처리량 (req/s) | 95.29 | | |
| 레이턴시 avg (ms) | 181.4 | | |
| 레이턴시 p50 (ms) | 134.3 | | |
| 레이턴시 p95 (ms) | 490.2 | | |
| 레이턴시 p99 (ms) | 706.6 | | |
| 레이턴시 max (ms) | 1,739.5 | | |
| 서버 에러율 | 0.00% | | |
| 비즈니스 400 비율 (참고) | 62.07% | | |

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

`notification-service` consumer lag는 유지 구간 중 파티션별 최대 `4~6`까지 일시적으로 증가했지만,
테스트 종료 직후 기존 수준인 파티션별 `1`로 회복했다. P3 비교의 핵심 지표는 아니지만, 별도 병목 후보로
추적할 가치가 있다.

after 측정 후 p95/p99 감소 폭, 처리량 변화, Spot 노드 CPU 변화를 비교해 외부 호출 병렬화 효과를 판단한다.

## 5. 비고 / 측정 시 주의

- **비즈니스 400은 정상**: 경합 시나리오에서 입찰가가 현재가 이하면 400이 반환되지만,
  이 400 응답도 외부 호출 2회를 모두 거친 뒤 반환되므로 측정 대상 핫패스를 그대로 경유한다.
  따라서 레이턴시 통계에 포함하고, 에러율(`bid_error_rate`)에는 5xx/타임아웃만 집계한다.
- **CDC 전파 지연**: setup 단계에서 경매 생성 후 State Store 반영까지 폴링 대기한다(최대 ~60s).
