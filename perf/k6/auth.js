// 부하 테스트용 인증/데이터 부트스트랩 헬퍼.
// setup() 단계에서 호출해 판매자/입찰자 계정과 측정 대상 경매를 만든다.
// 게이트웨이 경로: /api/users/**, /api/auctions/**, /api/bids/** (StripPrefix=1 후 각 서비스로 라우팅).

import http from 'k6/http';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// 회원가입 후 로그인해 accessToken을 반환한다. 이미 가입된 이메일이면 로그인만 시도.
export function signupAndLogin(baseUrl, email, password, nickname) {
  const signupRes = http.post(
    `${baseUrl}/api/users/signup`,
    JSON.stringify({ email, password, nickname }),
    { headers: JSON_HEADERS },
  );
  // 신규 가입(201) 또는 이미 존재(409)만 정상으로 보고, 그 외(5xx 등)는 즉시 중단해 부트스트랩 실패 원인을 명확히 한다.
  if (signupRes.status !== 201 && signupRes.status !== 409) {
    throw new Error(`회원가입 실패 (${email}): status=${signupRes.status} body=${signupRes.body}`);
  }
  // 이미 가입된 계정(409)은 로그인으로 토큰 확보.
  const res = http.post(
    `${baseUrl}/api/users/login`,
    JSON.stringify({ email, password }),
    { headers: JSON_HEADERS },
  );
  if (res.status !== 200) {
    throw new Error(`로그인 실패 (${email}): status=${res.status} body=${res.body}`);
  }
  return JSON.parse(res.body).accessToken;
}

// 경매 하나를 생성하고 auctionId를 반환한다. endsAt은 테스트 동안 마감되지 않도록 충분히 미래로.
export function createAuction(baseUrl, sellerToken, title) {
  const endsAt = new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString(); // 2시간 후
  const res = http.post(
    `${baseUrl}/api/auctions`,
    JSON.stringify({ title, startPrice: 10000, endsAt }),
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${sellerToken}` } },
  );
  if (res.status !== 201) {
    throw new Error(`경매 생성 실패: status=${res.status} body=${res.body}`);
  }
  return JSON.parse(res.body).id;
}

import { sleep } from 'k6';

// 경매가 입찰 가능 상태(streams 파이프라인 준비)인지 실제 입찰로 확인한다.
//
// 신규 경매는 입찰 전까지 currentPrice가 null이므로 currentPrice 폴링으로는 준비를 알 수 없다.
// bid 경로는 streams IQ가 404(입찰 없음)면 null로 보고 첫 입찰을 201로 받아들이지만,
// CDC→Streams 전파 전에는 streams 조회가 실패해 503이 날 수 있다(ExternalServiceException).
// 따라서 e2e와 동일하게 "입찰이 201로 받아들여질 때까지 재시도"하는 것이 올바른 준비 신호다.
//
// @param amount startPrice보다 큰 입찰가(첫 입찰이 시작가와 비교되므로)
// @return 준비되면 true, attempts 내 실패하면 false
export function probeBidUntilReady(baseUrl, token, auctionId, amount, attempts, intervalSec) {
  for (let i = 0; i < attempts; i++) {
    const res = http.post(
      `${baseUrl}/api/bids`,
      JSON.stringify({ auctionId, amount }),
      { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } },
    );
    if (res.status === 201) {
      return true;
    }
    sleep(intervalSec);
  }
  return false;
}
