# 데모 UI 실행 가이드

로컬 브라우저에서 실시간 경매 서비스를 직접 조작하며 시연하기 위한 가이드.
GKE에 배포된 서비스를 대상으로 동작한다.

관련 스크립트: `demo/`

---

## 사전 조건

- GKE Gateway IP 확보 (`kubectl get svc -n auction | grep gateway`)
- Python 3 설치 (macOS 기본 포함)
- Chrome / Safari 브라우저

---

## 1. 데모 UI 시작

```bash
./demo/start-demo.sh
```

내부적으로 하는 일:
1. `http://localhost:8888`에 HTTP 서버를 백그라운드로 실행
2. 브라우저에서 `http://localhost:8888/login.html`을 자동으로 열기

종료하려면 출력된 PID를 kill하거나 터미널을 닫으면 된다.

```bash
# 포트 변경이 필요한 경우
./demo/start-demo.sh 9000
```

---

## 2. 로그인 / 회원가입

브라우저에 열린 로그인 화면에서:

| 필드 | 값 |
|---|---|
| Gateway URL | `http://<gateway-ip>` |
| 이메일 | 원하는 이메일 (예: `test@test.local`) |
| 비밀번호 | 원하는 비밀번호 (8자 이상, 영문+숫자) |

- **[로그인]**: 이미 계정이 있는 경우
- **[회원가입 후 로그인]**: 새 계정 생성 + 자동 로그인

로그인 성공 시 경매 목록 페이지(`auctions.html`)로 자동 이동한다.

---

## 3. 경매 목록 / 생성

`auctions.html`에서:
- 진행 중인 경매 목록이 카드 형태로 표시된다 (30초마다 자동 갱신)
- **[+ 경매 생성]**: 제목, 시작가, 마감 시간(분)을 입력해 새 경매를 만든다
- 카드 클릭 시 해당 경매의 실시간 현재가 화면으로 이동한다

---

## 4. 실시간 현재가 화면

`auction-live.html`에서:
- 경매 목록에서 카드를 클릭하면 자동으로 WebSocket에 연결된다
- 입찰이 들어올 때마다 현재가가 초록색으로 깜빡이며 갱신된다
- 하단 로그 패널에 수신한 WebSocket 메시지가 실시간으로 표시된다

**두 창 동시 관람**: 같은 URL을 브라우저 창 두 개에 열면 양쪽에서 동시에 갱신되는 것을 확인할 수 있다.

---

## 5. 경매 데이터 자동 세팅

매번 수동으로 회원가입 → 경매 생성을 하지 않으려면 `setup-demo.sh`를 사용한다.
계정 2개 생성(판매자·입찰자) → 경매 생성 → 파이프라인 워밍업까지 자동으로 처리하고,
이어서 실행할 값(WS Base, Auction ID, JWT)을 출력한다.

```bash
BASE_URL=http://<gateway-ip> ./demo/setup-demo.sh
```

출력 예시:
```text
✅ 데모 준비 완료

[ auction-live.html 에 입력할 값 ]
  WebSocket Base : ws://34.64.x.x
  Auction ID     : xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
  JWT Token      : eyJhbGci...
```

`auction-live.html`을 직접 열었을 때 위 값을 수동으로 입력해도 된다.

> JWT는 15분 만료. 시간이 지나면 `setup-demo.sh`를 다시 실행한다.

---

## 6. 입찰 시나리오 실행

`run-scenario.sh`로 미리 정의된 입찰 패턴을 자동으로 실행할 수 있다.
`setup-demo.sh` 출력값을 그대로 환경변수로 넘긴다.

```bash
BASE=http://<gateway-ip> \
AUCTION_ID=<uuid> \
JWT=<token> \
./demo/run-scenario.sh <시나리오>
```

### 시나리오 목록

| 시나리오 | 설명 | 입찰 간격 | 확인할 것 |
|---|---|---|---|
| `sequential` | 11,000→18,000원, 1,000원씩 | 3초 | 현재가가 안정적으로 갱신되는 기본 흐름 |
| `rapid` | 11,000→50,000원, 1,000원씩 | 0.5초 | 고속 입찰에서도 WebSocket push가 누락 없이 전달되는지 |
| `compete` | 입찰자 2명이 교차 입찰 | 1~2초 | OUTBID 이벤트 발생 — 밀린 입찰자에게 알림 |
| `burst` | 10명이 동시에 서로 다른 금액으로 입찰 | 동시 | Kafka Streams State Store의 동시성 처리 |

### 실행 예시

```bash
# 빠른 연속 입찰 — 현재가가 빠르게 올라가는 장면
BASE=http://34.64.x.x AUCTION_ID=<uuid> JWT=<token> ./demo/run-scenario.sh rapid

# 2명 경쟁 입찰 — OUTBID 이벤트 확인
BASE=http://34.64.x.x AUCTION_ID=<uuid> JWT=<token> ./demo/run-scenario.sh compete

# 시나리오 목록 보기
./demo/run-scenario.sh help
```

---

## 전체 플로우 요약

```text
./demo/start-demo.sh
        ↓
  브라우저: login.html → 로그인
        ↓
  auctions.html → 경매 생성 또는 기존 경매 클릭
        ↓
  auction-live.html → WebSocket 자동 연결 (창 2개 나란히 열기)
        ↓
  터미널: run-scenario.sh <시나리오> 실행
        ↓
  브라우저 양쪽에서 현재가 실시간 갱신 확인
```
