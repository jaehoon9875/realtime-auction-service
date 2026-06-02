// P3: 입찰 API(POST /api/bids) 레이턴시 baseline 측정 스크립트.
//
// 측정 대상: BidService.placeBid 핫패스(auction-service 조회 + auction-streams IQ 조회 → 검증).
// 개선안(P3)은 이 두 외부 호출을 병렬화하는 것이므로, before/after에서 p95/p99 변화를 본다.
//
// 비즈니스 400(입찰가가 현재가 이하)은 경합 상황에서 정상적으로 발생한다.
// 그래도 400 응답은 외부 호출 2회를 모두 거친 뒤 반환되므로 측정 대상 핫패스를 그대로 경유한다.
// 따라서 레이턴시 통계에는 포함하고, 에러율(error)에는 5xx/타임아웃만 센다.

import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import {
  signupAndLogin,
  createAuction,
  probeBidUntilReady,
} from './auth.js';

// 준비 확인 시 사용할 입찰가(startPrice 10000 보다 커야 함). 실제 부하 입찰가는 이보다 훨씬 큼.
const PROBE_AMOUNT = 20000;

// ── 환경변수(베이스라인 설정에서 주입) ──────────────────────
const BASE_URL      = __ENV.BASE_URL      || 'http://api-gateway-service';
const MAX_VUS       = parseInt(__ENV.MAX_VUS)   || 20;
// SLEEP=0(무대기 프로파일)을 허용해야 하므로 `|| 0.3`로 처리하지 않는다(0이 falsy라 0.3으로 둔갑함).
const parsedSleep   = parseFloat(__ENV.SLEEP);
const SLEEP_SEC     = Number.isNaN(parsedSleep) ? 0.3 : parsedSleep;
const RAMPUP_DUR    = __ENV.RAMPUP_DUR    || '20s';
const SUSTAIN_DUR   = __ENV.SUSTAIN_DUR   || '2m';
const RAMPDOWN_DUR  = __ENV.RAMPDOWN_DUR  || '10s';
const AUCTION_COUNT = parseInt(__ENV.AUCTION_COUNT) || 10;
const BIDDER_COUNT  = parseInt(__ENV.BIDDER_COUNT)  || 5;
const SCENARIO      = __ENV.SCENARIO      || 'spread'; // spread | single
const RUN_ID        = __ENV.RUN_ID        || 'local';

// ── 커스텀 메트릭 ──────────────────────────────────────────
const bidErrorRate = new Rate('bid_error_rate');       // 5xx/타임아웃만
const bidRejected  = new Rate('bid_rejected_rate');    // 비즈니스 400 비율(참고)
const bidDuration  = new Trend('bid_duration_ms', true);

export const options = {
  // 계정·경매 생성 + 경매별 입찰 준비 확인(재시도 포함)이 기본 60s를 넘길 수 있어 넉넉히.
  setupTimeout: '300s',
  // k6 기본 요약에는 p(99)가 빠져 있어 명시적으로 추가(포트폴리오 핵심 지표).
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    bids: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMPUP_DUR,   target: MAX_VUS }, // 워밍업 겸 ramp-up
        { duration: SUSTAIN_DUR,  target: MAX_VUS }, // 측정 핵심 구간
        { duration: RAMPDOWN_DUR, target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    bid_error_rate:   ['rate<0.01'],   // 서버 오류는 1% 미만이어야
    bid_duration_ms:  ['p(95)<1000', 'p(99)<2000'],
  },
};

// setup: 판매자/입찰자 계정과 경매를 만들고 State Store 반영을 기다린다. (1회 실행)
export function setup() {
  const ts = Date.now();
  const sellerToken = signupAndLogin(
    BASE_URL, `perf-seller-${ts}@test.local`, 'PerfTest1!', `seller-${ts}`,
  );

  const auctionIds = [];
  for (let i = 0; i < AUCTION_COUNT; i++) {
    auctionIds.push(createAuction(BASE_URL, sellerToken, `Perf Auction ${ts}-${i}`));
  }

  const bidderTokens = [];
  for (let i = 0; i < BIDDER_COUNT; i++) {
    bidderTokens.push(
      signupAndLogin(BASE_URL, `perf-bidder-${ts}-${i}@test.local`, 'PerfTest1!', `bidder-${ts}-${i}`),
    );
  }

  // 준비 확인 — 입찰 가능 상태가 될 때까지 실제 입찰로 검증한다.
  // (1) 첫 경매로 CDC→Streams 전파 지연을 흡수(넉넉히 재시도).
  if (!probeBidUntilReady(BASE_URL, bidderTokens[0], auctionIds[0], PROBE_AMOUNT, 15, 3)) {
    throw new Error('경매 입찰 준비 실패(CDC/Streams 지연). 부하 시작 중단.');
  }
  // (2) 나머지 경매도 입찰 가능한지 확인(대개 즉시 통과). 시작 시 503 노이즈를 줄여 측정 정확도를 높임.
  for (let i = 1; i < auctionIds.length; i++) {
    if (!probeBidUntilReady(BASE_URL, bidderTokens[0], auctionIds[i], PROBE_AMOUNT, 5, 2)) {
      throw new Error(`경매[${i}] 입찰 준비 실패. 부하 시작 중단.`);
    }
  }

  return { auctionIds, bidderTokens };
}

// default: VU별로 입찰가를 점증시키며 입찰. (핫패스 반복 호출)
export default function (data) {
  const { auctionIds, bidderTokens } = data;

  const token = bidderTokens[__VU % bidderTokens.length];
  const auctionId = SCENARIO === 'single'
    ? auctionIds[0]
    : auctionIds[__VU % auctionIds.length];

  // 입찰가: VU/iteration에 따라 점증시켜 일부는 201 성공하도록(나머지는 정상적 400).
  const amount = 10000 + (__VU * 1000000) + (__ITER * 1000);

  const res = http.post(
    `${BASE_URL}/api/bids`,
    JSON.stringify({ auctionId, amount }),
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, timeout: '10s' },
  );

  bidDuration.add(res.timings.duration);
  bidErrorRate.add(res.status >= 500 || res.status === 0);
  bidRejected.add(res.status === 400);

  check(res, {
    'no server error (<500)': (r) => r.status < 500 && r.status !== 0,
  });

  if (SLEEP_SEC > 0) {
    sleep(SLEEP_SEC);
  }
}

import { sleep } from 'k6';

// handleSummary: 외부 jslib import 없이(egress 회피) 사람용 요약 + 기계용 JSON을 stdout에 출력.
// run-perf.sh가 마커 사이의 JSON을 추출해 perf/results/<git-sha>/summary.json 으로 저장한다.
export function handleSummary(data) {
  const m = data.metrics;
  const dur = m.bid_duration_ms ? m.bid_duration_ms.values : {};
  const reqs = m.http_reqs ? m.http_reqs.values : {};

  const lines = [
    '',
    '================ P3 입찰 레이턴시 baseline 요약 ================',
    `run_id        : ${RUN_ID}`,
    `scenario      : ${SCENARIO}`,
    `max_vus       : ${MAX_VUS}`,
    `total_reqs    : ${reqs.count != null ? reqs.count : 'n/a'}`,
    `req_per_sec   : ${reqs.rate != null ? reqs.rate.toFixed(2) : 'n/a'}`,
    `latency_avg   : ${fmt(dur.avg)} ms`,
    `latency_p50   : ${fmt(dur.med)} ms`,
    `latency_p95   : ${fmt(dur['p(95)'])} ms`,
    `latency_p99   : ${fmt(dur['p(99)'])} ms`,
    `latency_max   : ${fmt(dur.max)} ms`,
    `server_err    : ${rate(m.bid_error_rate)}`,
    `biz_400_rate  : ${rate(m.bid_rejected_rate)}`,
    '===============================================================',
    '',
  ];

  // setup()이 반환한 데이터에는 입찰자 JWT가 포함되므로, 저장 파일에 남기지 않도록 제거한다.
  if (data.setup_data) {
    delete data.setup_data;
  }

  return {
    stdout: lines.join('\n')
      + '\n===SUMMARY_JSON_START===\n'
      + JSON.stringify(data)
      + '\n===SUMMARY_JSON_END===\n',
  };
}

function fmt(v) {
  return v != null ? v.toFixed(1) : 'n/a';
}
function rate(metric) {
  if (!metric || metric.values.rate == null) return 'n/a';
  return (metric.values.rate * 100).toFixed(2) + '%';
}
