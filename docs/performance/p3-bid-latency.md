# P3 — 입찰 외부 호출 병렬화 (before/after)

> 측정 절차: [README.md](./README.md)

## 1. 개선 대상

`BidService.placeBid`(`services/bid-service/.../service/BidService.java`)는 입찰 핫패스에서
두 외부 호출을 **순차** 실행한다.

1. `auctionServiceClient.getAuction()` → auction-service REST
2. `auctionStreamsClient.getCurrentPrice()` → auction-streams IQ REST

두 조회는 `auctionId`만 입력으로 받아 서로 독립적인데 직렬로 실행되어, 입찰 API 레이턴시에
2 RTT가 그대로 가산된다.

**개선안**: 두 조회를 병렬 실행한 후 결과를 모아 기존 검증 순서(존재 → 상태 → 금액)를 유지한다.

**기대 효과**: `POST /api/bids` p95/p99 레이턴시 약 -1 RTT.

## 2. 측정 환경

| 항목 | 값 |
|------|-----|
| 클러스터 | GKE (`auction` 네임스페이스) |
| 부하 도구 | k6 (in-cluster Job, `perf/k6/bid-latency.js`) |
| 부하 설정 | `perf/config/baseline.env` (VU 50 / ramp-up 30s / 유지 5m / sleep 0.3s) |
| 시나리오 | spread (경매 20개에 입찰 분산) |
| 메트릭 | k6 요약 + Grafana(bid-service) |
| 노드/파드 리소스 | _(측정 시 기입)_ |
| Kafka 파티션 수 | _(측정 시 기입)_ |

> before/after는 위 설정을 **동일하게** 유지한 상태에서만 비교 가능하다.

## 3. 측정 결과

| 지표 | Baseline (개선 전) | After (개선 후) | 변화 |
|------|-------------------|----------------|------|
| git SHA | _(채우기)_ | _(채우기)_ | — |
| 측정 일시(UTC) | | | |
| 총 요청 수 | | | |
| 처리량 (req/s) | | | |
| 레이턴시 avg (ms) | | | |
| 레이턴시 p50 (ms) | | | |
| 레이턴시 p95 (ms) | | | |
| 레이턴시 p99 (ms) | | | |
| 서버 에러율 | | | |
| 비즈니스 400 비율 (참고) | | | |

> 원시 데이터(로컬, Git 제외): `perf/results/<git-sha>/summary-*.json`
> 외부 보관 링크: _(필요 시 기입)_

### Grafana 캡처

- Baseline: _(민감 정보를 제거한 이미지 또는 공유 링크)_
- After: _(민감 정보를 제거한 이미지 또는 공유 링크)_

## 4. 분석

_(측정 후 작성: p99가 얼마나 줄었는지, 기대했던 -1 RTT가 실제로 나타났는지,
처리량 변화, 예상과 다른 점이 있다면 그 원인.)_

## 5. 비고 / 측정 시 주의

- **비즈니스 400은 정상**: 경합 시나리오에서 입찰가가 현재가 이하면 400이 반환되지만,
  이 400 응답도 외부 호출 2회를 모두 거친 뒤 반환되므로 측정 대상 핫패스를 그대로 경유한다.
  따라서 레이턴시 통계에 포함하고, 에러율(`bid_error_rate`)에는 5xx/타임아웃만 집계한다.
- **CDC 전파 지연**: setup 단계에서 경매 생성 후 State Store 반영까지 폴링 대기한다(최대 ~60s).
