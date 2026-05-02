# API 명세

## 공통

### Base URL
```
http://localhost:8080/api   # 로컬 (API Gateway: 8080)
```

모든 요청은 API Gateway(`localhost:8080`)를 거친다.
Gateway는 경로 앞의 `/api` 세그먼트를 제거한 뒤 각 서비스로 라우팅한다.
예) `POST /api/users/signup` → user-service `POST /users/signup`

### 인증
JWT Bearer Token 방식. Access Token은 RSA RS256으로 서명된다.

- 인증이 필요한 API: `Authorization: Bearer {accessToken}` 헤더 필수
- Access Token 유효 시간: **15분**
- Refresh Token 유효 시간: **7일**

### 공통 에러 응답
```json
{
  "message": "에러 메시지"
}
```

| HTTP 상태 | 의미 |
|-----------|------|
| 400 | 클라이언트 요청 오류 (입력 형식, 인증 실패, 토큰 오류 등) |
| 401 | 유효하지 않은 Access Token (Gateway 또는 필터에서 즉시 차단) |
| 404 | 리소스 없음 |
| 503 | 의존 서비스 장애 (Redis 등) |

---

## User Service

Gateway 경로 prefix: `/api/users/**` → user-service `/users/**`

공개 경로(인증 불필요): `POST /api/users/signup`, `POST /api/users/login`, `POST /api/users/refresh`

---

### POST /api/users/signup
회원가입.

**Request Body**
```json
{
  "email": "user@example.com",
  "password": "password1234",
  "nickname": "재훈"
}
```

| 필드 | 타입 | 조건 |
|------|------|------|
| email | string | 필수, 이메일 형식 |
| password | string | 필수, 8자 이상 |
| nickname | string | 필수 |

**Response** `201 Created` (본문 없음)

**에러**
| 상태 | 메시지 | 원인 |
|------|--------|------|
| 400 | 이미 사용 중인 이메일입니다 | 중복 이메일 |
| 400 | (validation 메시지) | 입력 형식 오류 |

---

### POST /api/users/login
로그인. Access Token + Refresh Token 발급.

Refresh Token은 userId를 subject로 담은 서명된 JWT(RS256)이다.

**Request Body**
```json
{
  "email": "user@example.com",
  "password": "password1234"
}
```

**Response** `200 OK`
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ..."
}
```

**에러**
| 상태 | 메시지 | 원인 |
|------|--------|------|
| 400 | (자격증명 오류 메시지) | 이메일 없음 또는 비밀번호 불일치 |

---

### POST /api/users/refresh
Access Token 재발급. Refresh Token Rotation 적용 (재발급 시 Refresh Token도 함께 교체).

Refresh Token을 `Authorization: Bearer {refreshToken}` 헤더로 전달한다.

**Headers**: `Authorization: Bearer {refreshToken}` (필수)

**Response** `200 OK`
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ..."
}
```

**에러**
| 상태 | 메시지 | 원인 |
|------|--------|------|
| 400 | 만료되었거나 유효하지 않은 Refresh Token입니다 | TTL 만료 또는 이미 로그아웃된 세션 |
| 400 | 보안을 위해 세션이 무효화되었습니다. 다시 로그인해주세요 | 탈취 감지 (이전 Refresh Token 재사용) |

> **탈취 감지**: 서명은 유효하지만 현재 Redis에 저장된 토큰과 다를 경우
> 해당 userId의 세션 전체를 무효화하고 재로그인을 요구한다.

---

### POST /api/users/logout
로그아웃. Access Token으로 인증한 userId 기준으로 Redis의 Refresh Token을 삭제하여 세션 무효화.

**Headers**: `Authorization: Bearer {accessToken}` (필수)

**Response** `204 No Content` (본문 없음)

---

### GET /api/users/me
내 정보 조회.

**Headers**: `Authorization: Bearer {accessToken}` (필수)

**Response** `200 OK`
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "nickname": "재훈"
}
```

**에러**
| 상태 | 원인 |
|------|------|
| 401 | 유효하지 않은 Access Token |
| 404 | 사용자를 찾을 수 없음 |

---

## Auction Service

> M3에서 구현 예정. 아래는 설계 기준 명세이며 실제 구현 시 수정될 수 있다.

Gateway 경로 prefix: `/api/auctions/**` → auction-service `/auctions/**`

---

### POST /api/auctions
경매 생성.

**Headers**: `Authorization: Bearer {token}` (필수)

**Request**
```json
{
  "title": "맥북 프로 14인치",
  "description": "2023년형, 상태 양호",
  "startPrice": 1000000,
  "endsAt": "2024-05-01T18:00:00Z"
}
```

**Response** `201 Created`
```json
{
  "auctionId": "uuid",
  "title": "맥북 프로 14인치",
  "sellerId": "uuid",
  "startPrice": 1000000,
  "currentPrice": 1000000,
  "status": "PENDING",
  "endsAt": "2024-05-01T18:00:00Z",
  "createdAt": "2024-04-28T10:00:00Z"
}
```

---

### GET /api/auctions
경매 목록 조회. 페이징 + 상태 필터 지원.

**Query Parameters**
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| page | int | 0 | 페이지 번호 |
| size | int | 20 | 페이지 크기 |
| status | string | - | PENDING, ONGOING, CLOSED, CANCELLED |

**Response** `200 OK`
```json
{
  "content": [
    {
      "auctionId": "uuid",
      "title": "맥북 프로 14인치",
      "currentPrice": 1350000,
      "bidCount": 7,
      "status": "ONGOING",
      "endsAt": "2024-05-01T18:00:00Z"
    }
  ],
  "totalElements": 100,
  "totalPages": 5,
  "page": 0,
  "size": 20
}
```

---

### GET /api/auctions/{id}
경매 상세 조회. `currentPrice`는 DB가 아닌 Kafka Streams State Store에서 조회.

**Response** `200 OK`
```json
{
  "auctionId": "uuid",
  "title": "맥북 프로 14인치",
  "description": "2023년형, 상태 양호",
  "sellerId": "uuid",
  "startPrice": 1000000,
  "currentPrice": 1350000,
  "currentWinnerId": "uuid",
  "bidCount": 7,
  "status": "ONGOING",
  "endsAt": "2024-05-01T18:00:00Z",
  "createdAt": "2024-04-28T10:00:00Z"
}
```

---

### PATCH /api/auctions/{id}
경매 수정. 입찰이 없을 때만 가능.

**Headers**: `Authorization: Bearer {token}` (필수)

**Request**
```json
{
  "title": "맥북 프로 14인치 (수정)",
  "description": "설명 수정",
  "endsAt": "2024-05-02T18:00:00Z"
}
```

**Response** `200 OK`

---

### DELETE /api/auctions/{id}
경매 취소. 입찰이 없을 때만 가능.

**Headers**: `Authorization: Bearer {token}` (필수)

**Response** `204 No Content`

---

### GET /api/auctions/{id}/bids
경매 입찰 내역 조회.

**Response** `200 OK`
```json
{
  "content": [
    {
      "bidId": "uuid",
      "bidderId": "uuid",
      "amount": 1350000,
      "placedAt": "2024-04-28T15:30:00Z"
    }
  ],
  "totalElements": 7
}
```

---

## Bid Service

> M4에서 구현 예정. 아래는 설계 기준 명세이며 실제 구현 시 수정될 수 있다.

Gateway 경로 prefix: `/api/bids/**` → bid-service `/bids/**`

---

### POST /api/bids
입찰.

**Headers**: `Authorization: Bearer {token}` (필수)

**Request**
```json
{
  "auctionId": "uuid",
  "amount": 1400000
}
```

**Response** `201 Created`
```json
{
  "bidId": "uuid",
  "auctionId": "uuid",
  "amount": 1400000,
  "status": "ACCEPTED",
  "placedAt": "2024-04-28T15:30:00Z"
}
```

---

### GET /api/bids/me
내 입찰 내역 조회.

**Headers**: `Authorization: Bearer {token}` (필수)

**Response** `200 OK`
```json
{
  "content": [
    {
      "bidId": "uuid",
      "auctionId": "uuid",
      "amount": 1400000,
      "status": "ACCEPTED",
      "placedAt": "2024-04-28T15:30:00Z"
    }
  ],
  "totalElements": 3
}
```

---

## WebSocket (Notification Service)

> M6에서 구현 예정. 아래는 설계 기준 명세이며 실제 구현 시 수정될 수 있다.

Gateway 경로 prefix: `/ws/**` → notification-service `/ws/**`

### 연결
```
WS /ws/auctions/{id}    # 경매방 실시간 구독
WS /ws/users/me         # 개인 알림 구독
```

### 서버 → 클라이언트 메시지

**BID_UPDATED** - 최고가 갱신 시
```json
{
  "type": "BID_UPDATED",
  "auctionId": "uuid",
  "currentPrice": 1400000,
  "bidCount": 8,
  "occurredAt": "2024-04-28T15:30:00Z"
}
```

**AUCTION_CLOSED** - 경매 마감 시
```json
{
  "type": "AUCTION_CLOSED",
  "auctionId": "uuid",
  "finalPrice": 1400000,
  "winnerId": "uuid",
  "occurredAt": "2024-04-28T18:00:00Z"
}
```

**OUTBID** - 내가 입찰한 경매에서 더 높은 금액 입찰 시 (개인 알림)
```json
{
  "type": "OUTBID",
  "auctionId": "uuid",
  "currentPrice": 1500000,
  "occurredAt": "2024-04-28T15:35:00Z"
}
```

**AUCTION_WON** - 낙찰 시 (개인 알림)
```json
{
  "type": "AUCTION_WON",
  "auctionId": "uuid",
  "finalPrice": 1400000,
  "occurredAt": "2024-04-28T18:00:00Z"
}
```
